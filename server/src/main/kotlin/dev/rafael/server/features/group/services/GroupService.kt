package dev.rafael.server.features.group.services

import dev.rafael.contract.error.ErrorCodes
import dev.rafael.contract.group.CreateGroupRequest
import dev.rafael.contract.group.GroupDto
import dev.rafael.contract.group.GroupType
import dev.rafael.contract.group.MemberRole
import dev.rafael.contract.group.ScoringModel
import dev.rafael.core.result.AppError
import dev.rafael.core.result.AppResult
import dev.rafael.core.result.asFailure
import dev.rafael.core.result.flatMap
import dev.rafael.core.result.map
import dev.rafael.server.features.group.db.GroupRepository
import dev.rafael.server.features.group.db.NovoGrupo
import dev.rafael.server.features.group.models.toDto
import dev.rafael.server.features.checkin.services.CheckInPolicy
import dev.rafael.server.features.user.services.UserService
import kotlinx.datetime.LocalDate
import kotlin.time.Clock
import kotlin.uuid.Uuid

/**
 * Grupos (ARCH #33, fatia A.1): criar e ler.
 *
 * Entrada por link/código é a A.2 e não existe aqui — por isso [porId] recusa quem não é
 * membro. A tela de preview do convite, que é a única leitura pública da fase (2-B.0), chega
 * junto com a entrada; até lá, grupo é assunto de quem está dentro.
 */
/**
 * O que o grupo precisa saber sobre check-in — e **só isso**.
 *
 * `fun interface` e não o `CheckInRepository` inteiro: o grupo faz uma pergunta pontual, e receber
 * o repositório de outra feature daria a ele acesso a criar, apagar e listar check-ins. A porta
 * estreita mantém a dependência visível no construtor e trivial de dublar no teste.
 */
fun interface CheckInDeHoje {
    /** O id do check-in de [userId] neste grupo no [dia], ou `null`. */
    suspend fun idDe(groupId: Uuid, userId: Uuid, dia: LocalDate): Uuid?
}

class GroupService(
    private val userService: UserService,
    private val repository: GroupRepository,
    private val checkInDeHoje: CheckInDeHoje = CheckInDeHoje { _, _, _ -> null },
    private val clock: Clock = Clock.System,
) {

    suspend fun criar(
        firebaseUid: String,
        email: String?,
        req: CreateGroupRequest,
    ): AppResult<GroupDto> =
        userService.findOrCreate(firebaseUid, email).flatMap { user ->
            val agora = clock.now()
            GroupPolicy.validarCriacao(req, agora).flatMap { v ->
                repository.create(
                    NovoGrupo(
                        // Id gerado AQUI, como no usuário (A.0) e no outbox do cliente (#30):
                        // quem gera é quem manda, e o repositório não inventa identidade.
                        id = Uuid.random(),
                        type = GroupType.DESAFIO,
                        scoringModel = ScoringModel.CONTAGEM_CHECKINS,
                        title = v.titulo,
                        description = v.descricao,
                        startDate = v.inicio,
                        endDate = v.fim,
                        timezone = v.fuso,
                        rules = v.regras,
                        createdBy = user.id,
                    ),
                ).map { it.toDto(agora, MemberRole.ADMIN) }
            }
        }

    /**
     * Grupos de que a pessoa participa. Lista vazia é resposta legítima, não erro.
     *
     * **Os papéis vêm em UMA consulta, não uma por grupo.** A versão anterior chamava `roleOf`
     * dentro do `map`: com vinte grupos, vinte e uma consultas para montar uma tela. Não aparece
     * com dois grupos de teste e é péssimo com vinte — e é o tipo de defeito que se multiplica
     * sozinho, porque código novo imita o que já está no arquivo.
     *
     * `myCheckInToday` fica **fora** daqui de propósito: seria outra consulta por grupo, e a
     * lista não tem botão de check-in — quem tem é o detalhe.
     */
    suspend fun meusGrupos(firebaseUid: String, email: String?): AppResult<List<GroupDto>> =
        userService.findOrCreate(firebaseUid, email).flatMap { user ->
            val agora = clock.now()
            repository.listByMember(user.id).flatMap { grupos ->
                repository.rolesOf(grupos.map { it.id }, user.id).flatMap { papeis ->
                    // O papel vem por grupo; sem ele a tela não sabe se mostra as ações de admin.
                    AppResult.Success(
                        grupos.map { g -> g.toDto(agora, papeis[g.id]?.paraPapel()) },
                    )
                }
            }
        }

    /**
     * Um grupo, para QUEM É MEMBRO.
     *
     * ## O não-membro passou a saber que não é membro (G.2, ARCH #37)
     *
     * Antes os três caminhos daqui devolviam a MESMA frase, *"Grupo não encontrado"*, e a versão
     * anterior deste KDoc justificava: *"Forbidden confirmaria que o grupo existe, e com o id na
     * mão daria para enumerar grupos alheios"*.
     *
     * A troca foi reavaliada e **desfeita para o caso do não-membro**, com o motivo escrito:
     *
     * - o id do grupo é **UUID**, e ninguém enumera 2¹²² valores. A defesa protegia contra um
     *   ataque que não existe;
     * - o caso REAL é sair de um desafio e abrir depois uma notificação antiga dele. Dizer "não
     *   encontrado" ali faz a pessoa acreditar que o desafio **foi apagado**, quando ele está lá e
     *   ela é que saiu.
     *
     * Continua sendo **404**, e não 403: o status não muda, só o código e o texto. O que se ganha é
     * a pessoa entender o que aconteceu; o que se perde é a confirmação de existência, e ela custa
     * pouco aqui.
     *
     * ⚠️ **Isto NÃO vale para o perfil de terceiro** (`PERFIL_DE_TERCEIRO_INDISPONIVEL`). Lá o id
     * circula em notificação, link e feed, e a confirmação teria valor para quem estivesse
     * colecionando contas.
     */
    suspend fun porId(firebaseUid: String, email: String?, groupId: String): AppResult<GroupDto> =
        userService.findOrCreate(firebaseUid, email).flatMap { user ->
            val id = runCatching { Uuid.parse(groupId) }.getOrNull()
                ?: return@flatMap grupoNaoExiste()

            repository.roleOf(id, user.id).flatMap { papel ->
                if (papel == null) {
                    return@flatMap AppError.NotFound(
                        "Você não faz parte deste desafio.",
                        code = ErrorCodes.NAO_SOU_MEMBRO,
                    ).asFailure()
                }
                repository.findById(id).flatMap { grupo ->
                    // Corrida: a filiação existia e o grupo sumiu entre uma consulta e a outra.
                    if (grupo == null) return@flatMap grupoNaoExiste()
                    val agora = clock.now()
                    // "Hoje" no fuso do GRUPO (4.6) — a mesma conta do check-in, e por isso ela
                    // mora numa política só. Se cada lado calculasse o seu, um deles ficaria para
                    // trás no dia em que a regra mudasse.
                    val hoje = CheckInPolicy.diaDoGrupo(agora, grupo.timezone)
                    val meuCheckIn = checkInDeHoje.idDe(grupo.id, user.id, hoje)
                    AppResult.Success(
                        grupo.toDto(agora, papel.paraPapel(), meuCheckIn?.toString()),
                    )
                }
            }
        }

    /**
     * O desafio não existe mesmo: id malformado, ou apagado.
     *
     * Separado do [ErrorCodes.NAO_SOU_MEMBRO] na G.2. O id malformado cai aqui porque não há o que
     * confirmar: não existe grupo com aquele id em lugar nenhum.
     */
    private fun grupoNaoExiste(): AppResult<GroupDto> = AppError.NotFound(
        "Este desafio não existe mais.",
        code = ErrorCodes.GRUPO_NAO_EXISTE,
    ).asFailure()

    /** Papel gravado que não existe mais no enum: trata como MEMBRO, o menos privilegiado. */
    private fun String.paraPapel(): MemberRole =
        runCatching { MemberRole.valueOf(this) }.getOrDefault(MemberRole.MEMBRO)
}
