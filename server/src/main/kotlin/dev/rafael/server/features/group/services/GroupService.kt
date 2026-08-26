package dev.rafael.server.features.group.services

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
     * Não-membro recebe **NotFound**, não Forbidden. Forbidden confirmaria que o grupo existe,
     * e com o id na mão daria para enumerar grupos alheios — o mesmo cuidado que a regra de
     * visibilidade do perfil (#34) toma com o `userId`.
     */
    suspend fun porId(firebaseUid: String, email: String?, groupId: String): AppResult<GroupDto> =
        userService.findOrCreate(firebaseUid, email).flatMap { user ->
            val id = runCatching { Uuid.parse(groupId) }.getOrNull()
                ?: return@flatMap AppError.NotFound("Grupo não encontrado").asFailure()

            repository.roleOf(id, user.id).flatMap { papel ->
                if (papel == null) return@flatMap AppError.NotFound("Grupo não encontrado").asFailure()
                repository.findById(id).flatMap { grupo ->
                    if (grupo == null) return@flatMap AppError.NotFound("Grupo não encontrado").asFailure()
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

    /** Papel gravado que não existe mais no enum: trata como MEMBRO, o menos privilegiado. */
    private fun String.paraPapel(): MemberRole =
        runCatching { MemberRole.valueOf(this) }.getOrDefault(MemberRole.MEMBRO)
}
