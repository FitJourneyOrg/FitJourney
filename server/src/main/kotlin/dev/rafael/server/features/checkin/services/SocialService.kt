package dev.rafael.server.features.checkin.services

import dev.rafael.contract.checkin.CommentDto
import dev.rafael.contract.group.MemberRole
import dev.rafael.core.result.AppError
import dev.rafael.core.result.AppResult
import dev.rafael.core.result.asFailure
import dev.rafael.core.result.asSuccess
import dev.rafael.core.result.flatMap
import dev.rafael.core.result.map
import dev.rafael.server.features.checkin.db.CheckInRepository
import dev.rafael.server.features.checkin.db.SocialRepository
import dev.rafael.server.features.checkin.models.Comentario
import dev.rafael.server.features.checkin.models.NovoComentario
import dev.rafael.server.features.group.db.GroupRepository
import dev.rafael.server.features.user.models.User
import dev.rafael.server.features.user.services.UserService
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.uuid.Uuid

/**
 * Comentários e reações (fatia E.1, decisões 8.1 a 8.4).
 *
 * ## Toda operação passa por DUAS portas
 *
 * 1. **Sou membro do grupo?** — senão 404, nunca 403. Dizer "sem permissão" confirmaria que o
 *    grupo e o check-in existem, e a mesma escolha já vale no perfil público (C.1) e no check-in.
 * 2. **O check-in é DESTE grupo?** — sem isso, saber um id de check-in de outro grupo daria acesso
 *    a comentar nele. O id é um `Uuid` e não se adivinha, mas segurança que depende de o atacante
 *    não descobrir um identificador é segurança emprestada.
 */
class SocialService(
    private val userService: UserService,
    private val groups: GroupRepository,
    private val checkIns: CheckInRepository,
    private val repository: SocialRepository,
    private val clock: Clock = Clock.System,
    /**
     * Avisa o dono do check-in que alguém comentou (10.4, fatia F).
     *
     * **Default que não faz nada**, como no `FriendshipService`: o grafo funciona sem notificação,
     * e o comentário aparece no feed de qualquer jeito. Um serviço que EXIGISSE o notificador
     * amarraria comentar à disponibilidade do FCM.
     */
    private val avisarComentario: AvisarComentario = AvisarComentario { _, _, _, _, _ -> },
) {

    /**
     * Porta estreita para o push. `checkin` **não importa** `notificacao`.
     *
     * Cinco argumentos e nenhum objeto de domínio: para quem, quem comentou, o que escreveu, e
     * onde. Tudo que o aviso precisa, nada do que ele faz — o mesmo desenho do `AvisarPedido`.
     */
    fun interface AvisarComentario {
        suspend operator fun invoke(
            dono: Uuid,
            nomeDeQuemComentou: String,
            texto: String,
            groupId: Uuid,
            checkInId: Uuid,
        )
    }

    suspend fun comentarios(
        firebaseUid: String,
        email: String?,
        groupId: String,
        checkInId: String,
    ): AppResult<List<CommentDto>> = comMembro(firebaseUid, email, groupId) { user, papel ->
        comCheckIn(groupId, checkInId) { alvo, donoDoCheckIn ->
            repository.comentarios(alvo).map { lista ->
                lista.map { it.toDto(user, donoDoCheckIn, papel) }
            }
        }
    }

    suspend fun comentar(
        firebaseUid: String,
        email: String?,
        groupId: String,
        checkInId: String,
        texto: String,
    ): AppResult<CommentDto> = comMembro(firebaseUid, email, groupId) { user, papel ->
        // O texto tratado é o que se grava — não o que chegou. Validar um e gravar outro foi o
        // defeito do `display_name`, onde a tela mostrava dois espaços até o próximo sync.
        val corpo = SocialPolicy.comentarioValido(texto)
            ?: return@comMembro AppError.Validation(
                "Escreva algo, com até ${SocialPolicy.MAX_COMENTARIO} caracteres.",
                mapOf("body" to "Escreva algo, com até ${SocialPolicy.MAX_COMENTARIO} caracteres."),
            ).asFailure()

        comCheckIn(groupId, checkInId) { alvo, donoDoCheckIn ->
            repository.comentar(
                NovoComentario(
                    id = Uuid.random(),
                    checkInId = alvo,
                    groupId = Uuid.parse(groupId),
                    userId = user.id,
                    body = corpo,
                    createdAt = agora(),
                ),
            ).map { comentario ->
                // O aviso sai DEPOIS de gravar, e só se gravou (estamos dentro do `map`). Avisar
                // antes contaria um comentário que talvez não exista.
                //
                // **Não aviso a mim mesmo.** Comentar no próprio check-in é comum — a pessoa
                // responde a quem comentou nela —, e um push dizendo "você comentou no seu
                // check-in" é ruído puro.
                if (donoDoCheckIn != null && donoDoCheckIn != user.id) {
                    avisarComentario(
                        donoDoCheckIn,
                        user.displayName,
                        corpo,
                        Uuid.parse(groupId),
                        alvo,
                    )
                }
                comentario.toDto(user, donoDoCheckIn, papel)
            }
        }
    }

    /**
     * Apaga o comentário. **O autor, o dono do check-in ou o admin** (2026-09-06, emendado em 09-07).
     *
     * Sem prazo, ao contrário do check-in (4.11): comentário não vale ponto, então apagar não mexe
     * no ranking — e comentário ofensivo antigo precisa poder sair.
     */
    suspend fun apagarComentario(
        firebaseUid: String,
        email: String?,
        groupId: String,
        comentarioId: String,
    ): AppResult<Unit> = comMembro(firebaseUid, email, groupId) { user, papel ->
        val id = runCatching { Uuid.parse(comentarioId) }.getOrNull()
            ?: return@comMembro naoEncontrado()

        repository.comentario(id).flatMap { alvo ->
            // Não existe, ou é de outro grupo: 404 nos dois casos. Distinguir contaria que existe.
            if (alvo == null || alvo.groupId.toString() != groupId) return@flatMap naoEncontrado()

            // Quem é o dono do CHECK-IN comentado? Ele também pode apagar (emenda de 2026-09-07):
            // é a foto dele, e quem publica decide o que fica pendurado no próprio conteúdo.
            checkIns.porId(alvo.checkInId).flatMap { doCheckIn ->
                val dono = doCheckIn?.checkIn?.userId

                if (!SocialPolicy.podeApagarComentario(
                        souOAutor = alvo.userId == user.id,
                        souDonoDoCheckIn = dono == user.id,
                        meuPapel = papel,
                    )
                ) {
                    return@flatMap AppError.Forbidden(
                        "Só quem escreveu, quem fez o check-in, ou o admin do desafio pode apagar " +
                            "este comentário.",
                    ).asFailure()
                }
                repository.apagarComentario(id)
            }
        }
    }

    /**
     * Põe ou troca a minha reação (8.2). Uma por pessoa — a PK composta garante.
     *
     * O emoji é validado contra o vocabulário fechado da [SocialPolicy]. O banco aceitaria
     * qualquer coisa de propósito (a lista é decisão de produto e vai mudar), então **a recusa
     * mora aqui**.
     */
    suspend fun reagir(
        firebaseUid: String,
        email: String?,
        groupId: String,
        checkInId: String,
        emoji: String,
    ): AppResult<Unit> = comMembro(firebaseUid, email, groupId) { user, _ ->
        if (!SocialPolicy.reacaoValida(emoji)) {
            return@comMembro AppError.Validation("Esta reação não existe.").asFailure()
        }
        comCheckIn(groupId, checkInId) { alvo, _ ->
            repository.reagir(alvo, Uuid.parse(groupId), user.id, emoji, agora())
        }
    }

    /** Tira a minha reação. Idempotente: tirar o que não existe devolve sucesso. */
    suspend fun desreagir(
        firebaseUid: String,
        email: String?,
        groupId: String,
        checkInId: String,
    ): AppResult<Unit> = comMembro(firebaseUid, email, groupId) { user, _ ->
        comCheckIn(groupId, checkInId) { alvo, _ ->
            repository.desreagir(alvo, user.id)
        }
    }

    // ---- utilidades ----

    private fun agora() = clock.now().toLocalDateTime(TimeZone.UTC)

    private fun Comentario.toDto(
        quemPede: User,
        donoDoCheckIn: Uuid?,
        papel: MemberRole?,
    ) = CommentDto(
        id = id.toString(),
        checkInId = checkInId.toString(),
        userId = userId.toString(),
        displayName = displayName,
        body = body,
        createdAt = createdAt.toString(),
        // Resolvido no SERVIDOR, como o `canDelete` do check-in: a tela não compara ids, não
        // consulta papel e não sabe de quem é o check-in para decidir o que desenhar.
        canDelete = SocialPolicy.podeApagarComentario(
            souOAutor = userId == quemPede.id,
            souDonoDoCheckIn = donoDoCheckIn == quemPede.id,
            meuPapel = papel,
        ),
        // Fatia E.2. Apagar e denunciar convivem no mesmo comentário sem contradição: quem pode
        // apagar resolve na hora, quem não pode encaminha ao admin.
        canReport = ModeracaoPolicy.podeDenunciarComentario(
            criadoEm = createdAt,
            agora = clock.now(),
            souOAutor = userId == quemPede.id,
        ) == null,
    )

    /**
     * Guarda de FILIAÇÃO, e devolve o papel junto.
     *
     * O papel vem no mesmo passo porque quem apaga comentário precisa dele, e consultá-lo de novo
     * seria uma segunda ida ao banco pelo dado que a guarda acabou de ler.
     */
    private suspend fun <T> comMembro(
        firebaseUid: String,
        email: String?,
        groupId: String,
        bloco: suspend (User, MemberRole?) -> AppResult<T>,
    ): AppResult<T> = userService.findOrCreate(firebaseUid, email).flatMap { user ->
        val id = runCatching { Uuid.parse(groupId) }.getOrNull() ?: return@flatMap naoEncontrado()
        groups.roleOf(id, user.id).flatMap { papel ->
            if (papel == null) return@flatMap naoEncontrado()
            bloco(user, runCatching { MemberRole.valueOf(papel) }.getOrNull())
        }
    }

    /**
     * O check-in existe e é DESTE grupo? Entrega o id **e o dono**.
     *
     * Sem esta conferência, conhecer um id de check-in de outro grupo daria acesso a comentar e
     * reagir nele — e o `comMembro` acima já teria passado, porque a pessoa é membro de ALGUM
     * grupo. **As duas guardas verificam coisas diferentes.**
     *
     * O dono sai junto porque a busca já aconteceu: quem pode apagar comentário inclui o dono do
     * check-in (emenda de 2026-09-07), e consultá-lo depois seria uma segunda ida ao banco pelo
     * dado que esta guarda acabou de ler.
     */
    private suspend fun <T> comCheckIn(
        groupId: String,
        checkInId: String,
        bloco: suspend (Uuid, Uuid?) -> AppResult<T>,
    ): AppResult<T> {
        val id = runCatching { Uuid.parse(checkInId) }.getOrNull() ?: return naoEncontrado()
        return checkIns.porId(id).flatMap { achado ->
            val alvo = achado?.checkIn
            if (alvo == null || alvo.groupId.toString() != groupId) return@flatMap naoEncontrado()
            bloco(id, alvo.userId)
        }
    }

    /** 404 para tudo que não é meu, nunca 403 — "sem permissão" confirmaria a existência. */
    private fun <T> naoEncontrado(): AppResult<T> =
        AppError.NotFound("Não encontramos este item.").asFailure()
}
