package dev.rafael.server.features.checkin.services

import dev.rafael.contract.checkin.CheckInStatus
import dev.rafael.contract.checkin.CommentDto
import dev.rafael.contract.checkin.ReportItemDto
import dev.rafael.contract.checkin.ReportTarget
import dev.rafael.contract.group.MemberRole
import dev.rafael.core.result.AppError
import dev.rafael.core.result.AppResult
import dev.rafael.core.result.asFailure
import dev.rafael.core.result.asSuccess
import dev.rafael.core.result.flatMap
import dev.rafael.core.result.map
import dev.rafael.server.features.checkin.db.CheckInRepository
import dev.rafael.server.features.checkin.db.ModeracaoRepository
import dev.rafael.server.features.checkin.db.SocialRepository
import dev.rafael.server.features.checkin.models.AcaoDeModeracao
import dev.rafael.server.features.checkin.models.CasoAberto
import dev.rafael.server.features.checkin.models.Comentario
import dev.rafael.server.features.checkin.models.NovaDenuncia
import dev.rafael.server.features.checkin.models.TipoDeAcao
import dev.rafael.server.features.checkin.models.toDto
import dev.rafael.server.features.group.db.GroupRepository
import dev.rafael.server.features.group.models.Group
import dev.rafael.server.features.user.models.User
import dev.rafael.server.features.user.services.UserService
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.uuid.Uuid

/** Códigos de erro que a tela distingue (#31). Frase é do cliente; o servidor manda o código. */
const val CODE_PRAZO_DA_DENUNCIA = "denuncia_fora_do_prazo"
const val CODE_JA_DENUNCIEI = "denuncia_repetida"

/**
 * DENÚNCIA E MODERAÇÃO (fatia E.2, ARCH #33 seção 6).
 *
 * ## O mecanismo de correção é o ÚNICO que existe
 *
 * Sem votação (o #17 foi emendado), nada é verificado no momento do check-in: foto, local e emoji
 * são autodeclarações. Este serviço é a totalidade da correção — se ele falha, a única resposta do
 * produto à fraude é sair do grupo.
 *
 * ## Três guardas, e nenhuma é redundante
 *
 * 1. **Sou membro?** — senão 404, nunca 403. Igual ao resto da fatia.
 * 2. **O alvo é DESTE grupo?** — sem isso, conhecer um id daria acesso a denunciar em qualquer
 *    grupo do app, e a fila do admin encheria de coisa que ele não pode nem abrir.
 * 3. **Sou admin?** — só para a fila e o julgamento (6.2 + 6.7). E aqui a recusa é **403 e não
 *    404**, ao contrário de todo o resto: o membro comum SABE que o grupo existe — ele está dentro
 *    dele. Esconder seria mentira sem ganho.
 */
class ModeracaoService(
    private val userService: UserService,
    private val groups: GroupRepository,
    private val checkIns: CheckInRepository,
    private val social: SocialRepository,
    private val repository: ModeracaoRepository,
    private val clock: Clock = Clock.System,
    /**
     * Os três avisos desta fatia (10.4, fatia F). **Defaults que não fazem nada**, como no resto:
     * denunciar e julgar funcionam sem FCM, e a fila é o piso para o admin.
     */
    private val avisos: AvisosDeModeracao = AvisosDeModeracao(),
) {

    /**
     * As portas estreitas para o push. `checkin` **não importa** `notificacao`.
     *
     * Agrupadas num objeto e não como três parâmetros soltos: são a mesma preocupação, entram e
     * saem juntas, e três `fun interface` no construtor tornariam a chamada do Koin ilegível. Cada
     * campo continua sendo uma função de tipos simples — nenhum objeto de domínio atravessa.
     */
    data class AvisosDeModeracao(
        /** Ao denunciado. Não diz quem denunciou nem o motivo — ver o KDoc do `Aviso`. */
        val denunciaContraMim: suspend (dono: Uuid, groupId: Uuid, ehComentario: Boolean) -> Unit =
            { _, _, _ -> },
        /** Ao admin, **uma por caso novo** e não por denúncia (6.11). */
        val novaDenunciaNoGrupo: suspend (admin: Uuid, nomeDoGrupo: String, groupId: Uuid) -> Unit =
            { _, _, _ -> },
        /** Ao dono, quando o check-in dele é invalidado. */
        val checkInInvalidado: suspend (dono: Uuid, nomeDoGrupo: String, groupId: Uuid) -> Unit =
            { _, _, _ -> },
    )

    // ---- denunciar ----

    /** 6.1: qualquer membro denuncia um check-in que não é seu, dentro de 7 dias. */
    suspend fun denunciarCheckIn(
        firebaseUid: String,
        email: String?,
        groupId: String,
        checkInId: String,
        motivo: String?,
    ): AppResult<Unit> = comMembro(firebaseUid, email, groupId) { grupo, user, _ ->
        val texto = ModeracaoPolicy.motivoValido(motivo) ?: return@comMembro semMotivo()
        val id = runCatching { Uuid.parse(checkInId) }.getOrNull() ?: return@comMembro naoEncontrado()

        checkIns.porId(id).flatMap { achado ->
            val alvo = achado?.checkIn
            if (alvo == null || alvo.groupId != grupo.id) return@flatMap naoEncontrado()

            ModeracaoPolicy.podeDenunciarCheckIn(
                status = alvo.status,
                criadoEm = alvo.createdAt,
                agora = clock.now(),
                souOAutor = alvo.userId == user.id,
            )?.let { return@flatMap recusa(it) }

            gravar(grupo.id, ReportTarget.CHECK_IN, id, user.id, texto).flatMap { novidade ->
                // Só a PRIMEIRA denúncia move o estado. As seguintes engrossam o contador (6.11) —
                // e é isto que faz "várias viram uma solicitação" valer também para o status, não
                // só para a tela do admin.
                if (!novidade) return@flatMap AppError.Conflict(
                    "Você já denunciou este check-in.",
                    CODE_JA_DENUNCIEI,
                ).asFailure()

                val proximo = ModeracaoPolicy.aposDenuncia(alvo.status)
                // `casoNovo` usa a MESMA condição que move o estado, e não é coincidência: o caso
                // é novo exatamente quando o check-in sai de VALIDO. Avisar o admin toda vez faria
                // cinco denúncias no mesmo alvo virarem cinco pushes para um item só de fila.
                val casoNovo = proximo != alvo.status
                avisar(grupo, alvo.userId, casoNovo, ehComentario = false)

                if (!casoNovo) Unit.asSuccess() else checkIns.atualizarStatus(id, proximo)
            }
        }
    }

    /**
     * 6.4: comentário também se denuncia.
     *
     * Sem mexer em estado nenhum — comentário não tem `status`. O caso entra na fila e, se o admin
     * acatar, o comentário é **removido**.
     */
    suspend fun denunciarComentario(
        firebaseUid: String,
        email: String?,
        groupId: String,
        comentarioId: String,
        motivo: String?,
    ): AppResult<Unit> = comMembro(firebaseUid, email, groupId) { grupo, user, _ ->
        val texto = ModeracaoPolicy.motivoValido(motivo) ?: return@comMembro semMotivo()
        val id = runCatching { Uuid.parse(comentarioId) }.getOrNull() ?: return@comMembro naoEncontrado()

        social.comentario(id).flatMap { alvo ->
            if (alvo == null || alvo.groupId != grupo.id) return@flatMap naoEncontrado()

            ModeracaoPolicy.podeDenunciarComentario(
                criadoEm = alvo.createdAt,
                agora = clock.now(),
                souOAutor = alvo.userId == user.id,
            )?.let { return@flatMap recusa(it) }

            gravar(grupo.id, ReportTarget.COMMENT, id, user.id, texto).flatMap { novidade ->
                if (!novidade) {
                    return@flatMap AppError.Conflict(
                        "Você já denunciou este comentário.",
                        CODE_JA_DENUNCIEI,
                    ).asFailure()
                }
                // Comentário não tem estado, então "caso novo" não sai de uma transição — sai da
                // fila. Se já havia denúncia aberta neste comentário, o admin já foi avisado.
                repository.casoAberto(grupo.id, id).flatMap { caso ->
                    avisar(grupo, alvo.userId, casoNovo = caso == null || caso.quantidade == 1, ehComentario = true)
                    Unit.asSuccess()
                }
            }
        }
    }

    // ---- fila do admin ----

    /** A fila (6.2). Só o admin — e a recusa aqui é 403, ver o KDoc da classe. */
    suspend fun fila(
        firebaseUid: String,
        email: String?,
        groupId: String,
    ): AppResult<List<ReportItemDto>> = comAdmin(firebaseUid, email, groupId) { grupo, user ->
        repository.fila(grupo.id).flatMap { casos -> hidratar(casos, grupo, user) }
    }

    /** Quantos casos esperam. É o badge da toolbar — mesma guarda da fila. */
    suspend fun pendentes(
        firebaseUid: String,
        email: String?,
        groupId: String,
    ): AppResult<Int> = comAdmin(firebaseUid, email, groupId) { grupo, _ ->
        repository.pendentes(grupo.id)
    }

    /**
     * O JULGAMENTO (6.3, 6.6). Acatar invalida o check-in ou remove o comentário.
     *
     * **A ordem das três escritas importa.** O registro append-only vem PRIMEIRO, e o motivo é o
     * comentário: acatar uma denúncia de comentário o apaga, e a linha de `group_reports` vai junto
     * por CASCADE. Se a auditoria fosse a última, a decisão de remover um comentário seria a única
     * que não deixaria rastro — exatamente a que mais precisa dele.
     */
    suspend fun julgar(
        firebaseUid: String,
        email: String?,
        groupId: String,
        alvoId: String,
        acatar: Boolean,
    ): AppResult<Unit> = comAdmin(firebaseUid, email, groupId) { grupo, user ->
        val id = runCatching { Uuid.parse(alvoId) }.getOrNull() ?: return@comAdmin naoEncontrado()

        repository.casoAberto(grupo.id, id).flatMap { caso ->
            if (caso == null) return@flatMap naoEncontrado()
            val agora = clock.now().toLocalDateTime(TimeZone.UTC)

            val acao = when (caso.alvo) {
                ReportTarget.CHECK_IN ->
                    if (acatar) TipoDeAcao.INVALIDAR_CHECK_IN else TipoDeAcao.MANTER_CHECK_IN
                ReportTarget.COMMENT ->
                    if (acatar) TipoDeAcao.REMOVER_COMENTARIO else TipoDeAcao.MANTER_COMENTARIO
            }

            repository.registrar(
                AcaoDeModeracao(
                    id = Uuid.random(),
                    groupId = grupo.id,
                    adminId = user.id,
                    alvo = caso.alvo,
                    alvoId = id,
                    acao = acao,
                    createdAt = agora,
                ),
            ).flatMap {
                repository.resolver(id, agora).flatMap {
                    when (caso.alvo) {
                        ReportTarget.CHECK_IN ->
                            checkIns.atualizarStatus(id, ModeracaoPolicy.aposJulgamento(acatar))
                                .also {
                                    // Só quando ACATA. Manter não avisa ninguém: o denunciado
                                    // saberia da denúncia por um aviso que diz "não deu em nada" —
                                    // e ele já foi avisado quando ela chegou. Dois pushes para
                                    // dizer que nada mudou é pior que um.
                                    if (acatar) avisarInvalidacao(grupo, id)
                                }
                        ReportTarget.COMMENT ->
                            if (acatar) social.apagarComentario(id) else Unit.asSuccess()
                    }
                }
            }
        }
    }

    /**
     * 6.10: o admin invalida direto, sem denúncia prévia.
     *
     * Caminho separado do [julgar] de propósito — ele não tem caso a fechar, e forçá-lo pelo mesmo
     * método exigiria fabricar uma denúncia falsa só para julgá-la. O registro append-only é o
     * mesmo, e é o que faz as duas rotas produzirem a mesma auditoria.
     */
    suspend fun invalidarDireto(
        firebaseUid: String,
        email: String?,
        groupId: String,
        checkInId: String,
    ): AppResult<Unit> = comAdmin(firebaseUid, email, groupId) { grupo, user ->
        val id = runCatching { Uuid.parse(checkInId) }.getOrNull() ?: return@comAdmin naoEncontrado()

        checkIns.porId(id).flatMap { achado ->
            val alvo = achado?.checkIn
            if (alvo == null || alvo.groupId != grupo.id) return@flatMap naoEncontrado()
            if (alvo.status == CheckInStatus.INVALIDADO) return@flatMap Unit.asSuccess()

            val agora = clock.now().toLocalDateTime(TimeZone.UTC)
            repository.registrar(
                AcaoDeModeracao(
                    id = Uuid.random(),
                    groupId = grupo.id,
                    adminId = user.id,
                    alvo = ReportTarget.CHECK_IN,
                    alvoId = id,
                    acao = TipoDeAcao.INVALIDAR_CHECK_IN,
                    createdAt = agora,
                ),
            ).flatMap {
                // Se havia denúncias abertas, o ato do admin as resolve — julgar de novo o que ele
                // já decidiu seria pedir a mesma decisão duas vezes.
                repository.resolver(id, agora).flatMap {
                    checkIns.atualizarStatus(id, CheckInStatus.INVALIDADO)
                        // Mesmo aviso do julgamento acatado. Os dois caminhos levam ao mesmo lugar
                        // para quem recebe: o check-in dele parou de contar. **Quem perde o ponto
                        // não precisa saber por qual rota o admin passou.**
                        .also { avisos.checkInInvalidado(alvo.userId, grupo.title, grupo.id) }
                }
            }
        }
    }

    // ---- utilidades ----

    /**
     * Os dois avisos da denúncia: ao **denunciado**, sempre; ao **admin**, só em caso novo (6.11).
     *
     * Nenhum dos dois derruba a denúncia se falhar — as portas são `Unit` e o `NotificacaoService`
     * já engole o erro. A denúncia gravada é o que importa; o push é o aviso.
     *
     * O admin sai da lista de membros, e não de um método novo no repositório: o teto é 50 e a
     * consulta acontece uma vez por caso novo, que é raro. Um `adminDe(groupId)` seria mais
     * eficiente e mais uma coisa para manter em sincronia com a 2.12 (admin sem sucessor).
     */
    private suspend fun avisar(
        grupo: Group,
        donoDoConteudo: Uuid,
        casoNovo: Boolean,
        ehComentario: Boolean,
    ) {
        avisos.denunciaContraMim(donoDoConteudo, grupo.id, ehComentario)
        if (!casoNovo) return

        val membros = (groups.members(grupo.id) as? AppResult.Success)?.value ?: return
        membros.filter { it.role == MemberRole.ADMIN.name }
            // Um grupo tem sempre UM admin (invariante), mas iterar é mais barato que assumir: se
            // a 2.12 um dia permitir dois, isto continua correto sem ninguém lembrar de mexer.
            .forEach { avisos.novaDenunciaNoGrupo(it.userId, grupo.title, grupo.id) }
    }

    /**
     * Avisa o dono do check-in invalidado. Busca o dono porque o `julgar` só tem o id do alvo.
     *
     * Uma consulta a mais, e só no caminho de acatar — que é o raro. A alternativa seria carregar o
     * dono em todo julgamento, inclusive nos que mantêm.
     */
    private suspend fun avisarInvalidacao(grupo: Group, checkInId: Uuid) {
        val dono = (checkIns.porId(checkInId) as? AppResult.Success)?.value?.checkIn?.userId ?: return
        avisos.checkInInvalidado(dono, grupo.title, grupo.id)
    }

    private suspend fun gravar(
        grupo: Uuid,
        alvo: ReportTarget,
        alvoId: Uuid,
        denunciante: Uuid,
        motivo: String,
    ): AppResult<Boolean> = repository.denunciar(
        NovaDenuncia(
            id = Uuid.random(),
            groupId = grupo,
            alvo = alvo,
            alvoId = alvoId,
            denuncianteId = denunciante,
            motivo = motivo,
            createdAt = clock.now().toLocalDateTime(TimeZone.UTC),
        ),
    )

    /**
     * Carrega o conteúdo de cada caso, em DUAS consultas — não uma por item.
     *
     * A fila do admin é uma lista; buscar o check-in dentro do laço seria o N+1 que o feed já
     * evita. Os ids saem separados por tipo, e cada tipo faz sua única ida ao banco.
     *
     * Casos cujo conteúdo sumiu (comentário apagado enquanto a fila estava aberta) são
     * **descartados**, não mostrados vazios: não há o que o admin decida sobre o que não existe.
     */
    private suspend fun hidratar(
        casos: List<CasoAberto>,
        grupo: Group,
        quemPede: User,
    ): AppResult<List<ReportItemDto>> {
        if (casos.isEmpty()) return emptyList<ReportItemDto>().asSuccess()
        val agora = clock.now()

        val comentarios = mutableMapOf<Uuid, Comentario>()
        casos.filter { it.alvo == ReportTarget.COMMENT }.forEach { caso ->
            when (val r = social.comentario(caso.alvoId)) {
                is AppResult.Failure -> return r
                is AppResult.Success -> r.value?.let { comentarios[caso.alvoId] = it }
            }
        }

        return casos.mapNotNull { caso ->
            val base = ReportItemDto(
                targetId = caso.alvoId.toString(),
                target = caso.alvo,
                count = caso.quantidade,
                reasons = caso.motivos,
                firstReportedAt = caso.primeiraEm.toInstant(TimeZone.UTC).toString(),
            )
            when (caso.alvo) {
                ReportTarget.CHECK_IN -> when (val r = checkIns.porId(caso.alvoId)) {
                    is AppResult.Failure -> return r
                    is AppResult.Success -> r.value?.let {
                        base.copy(checkIn = it.toDto(quemPede.id, agora, grupo.timezone))
                    }
                }
                ReportTarget.COMMENT -> comentarios[caso.alvoId]?.let {
                    base.copy(
                        comment = CommentDto(
                            id = it.id.toString(),
                            checkInId = it.checkInId.toString(),
                            userId = it.userId.toString(),
                            displayName = it.displayName,
                            body = it.body,
                            createdAt = it.createdAt.toInstant(TimeZone.UTC).toString(),
                            // O admin age pela FILA, não pelos botões do card: mostrar "apagar" e
                            // "denunciar" aqui daria dois caminhos para a mesma decisão, e um deles
                            // não passaria pela auditoria.
                            canDelete = false,
                            canReport = false,
                        ),
                    )
                }
            }
        }.asSuccess()
    }

    /** Guarda de FILIAÇÃO, com o papel junto — o mesmo desenho do [SocialService]. */
    private suspend fun <T> comMembro(
        firebaseUid: String,
        email: String?,
        groupId: String,
        bloco: suspend (Group, User, MemberRole?) -> AppResult<T>,
    ): AppResult<T> = userService.findOrCreate(firebaseUid, email).flatMap { user ->
        val id = runCatching { Uuid.parse(groupId) }.getOrNull() ?: return@flatMap naoEncontrado()
        groups.findById(id).flatMap { grupo ->
            if (grupo == null) return@flatMap naoEncontrado()
            groups.roleOf(id, user.id).flatMap { papel ->
                if (papel == null) return@flatMap naoEncontrado()
                bloco(grupo, user, runCatching { MemberRole.valueOf(papel) }.getOrNull())
            }
        }
    }

    /**
     * Guarda de ADMIN (6.2 + 6.7). **403 e não 404**, e é a única exceção da fatia.
     *
     * Em todo o resto, esconder a existência é o certo: quem está de fora não deve saber que o
     * grupo existe. Aqui quem chega já é membro — ele vê o grupo, o feed e os outros membros. Um
     * 404 diria "este grupo não existe" para alguém que está olhando para ele.
     */
    private suspend fun <T> comAdmin(
        firebaseUid: String,
        email: String?,
        groupId: String,
        bloco: suspend (Group, User) -> AppResult<T>,
    ): AppResult<T> = comMembro(firebaseUid, email, groupId) { grupo, user, papel ->
        if (!ModeracaoPolicy.podeModerar(papel)) {
            return@comMembro AppError.Forbidden("Só o admin do desafio julga denúncias.").asFailure()
        }
        bloco(grupo, user)
    }

    private fun <T> recusa(bloco: DenunciaBlock): AppResult<T> = when (bloco) {
        DenunciaBlock.PRAZO -> AppError.Conflict(
            "Denúncias só valem até ${ModeracaoPolicy.PRAZO_EM_DIAS} dias.",
            CODE_PRAZO_DA_DENUNCIA,
        )
        // 403 e não 404: quem chegou aqui está olhando para o próprio conteúdo — esconder a
        // existência dele seria absurdo. A tela nem oferece o botão (`canReport`); isto é a rede
        // de segurança de quem chamou a rota direto.
        DenunciaBlock.E_SEU -> AppError.Forbidden("Não dá para denunciar o próprio conteúdo.")
        DenunciaBlock.JA_JULGADO -> AppError.Conflict("Este check-in já foi invalidado.")
    }.asFailure()

    private fun <T> semMotivo(): AppResult<T> = AppError.Validation(
        "Escreva o motivo da denúncia.",
        mapOf("reason" to "Escreva o motivo da denúncia."),
    ).asFailure()

    private fun <T> naoEncontrado(): AppResult<T> =
        AppError.NotFound("Não encontramos este item.").asFailure()
}
