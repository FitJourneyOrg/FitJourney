package dev.rafael.server.features.group.services

import dev.rafael.contract.group.GroupDto
import dev.rafael.contract.group.GroupInviteDto
import dev.rafael.contract.group.GroupPreviewDto
import dev.rafael.contract.group.JoinBlock
import dev.rafael.contract.group.MemberRole
import dev.rafael.core.result.AppError
import dev.rafael.core.result.AppResult
import dev.rafael.core.result.asFailure
import dev.rafael.core.result.asSuccess
import dev.rafael.core.result.flatMap
import dev.rafael.server.features.group.db.GroupRepository
import dev.rafael.server.features.group.models.Group
import dev.rafael.server.features.group.models.toDto
import dev.rafael.server.features.user.services.UserService
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * ENTRAR, SAIR e mandar no grupo (ARCH #33, fatia A.2).
 *
 * Separado do [GroupService] de propósito: aquele cria e lê, este mexe em quem está dentro. São
 * dois conjuntos de regras que quase não se tocam, e juntá-los daria uma classe que ninguém
 * consegue ler inteira — o `ProgramService` já é o exemplo do que evitar.
 */
class GroupMembershipService(
    private val userService: UserService,
    private val repository: GroupRepository,
    private val clock: Clock = Clock.System,
) {

    /**
     * PREVIEW — a única leitura da fase que responde a quem NÃO é membro (2-B.0).
     *
     * Devolve o mínimo, e nunca `NotFound` disfarçado: se o código ou o link não existem, aí sim
     * é 404. Mas grupo que existe e não aceita entrada responde 200 com `joinable = false` e o
     * motivo — a pessoa precisa entender o que houve, e "não encontrado" para um grupo que ela
     * está vendo o link seria mentira.
     */
    suspend fun preview(
        firebaseUid: String,
        email: String?,
        code: String?,
        inviteToken: String?,
    ): AppResult<GroupPreviewDto> =
        userService.findOrCreate(firebaseUid, email).flatMap { user ->
            val agora = clock.now()

            // Por LINK: o token pode estar revogado ou vencido, e isso é um bloqueio próprio —
            // o código do grupo pode continuar valendo, então não é o mesmo que "não existe".
            if (inviteToken != null) {
                val token = runCatching { Uuid.parse(inviteToken) }.getOrNull()
                    ?: return@flatMap naoEncontrado()
                return@flatMap repository.findInvite(token).flatMap { convite ->
                    if (convite == null) return@flatMap naoEncontrado()
                    repository.findById(convite.groupId).flatMap { grupo ->
                        if (grupo == null) return@flatMap naoEncontrado()
                        val vencido = convite.revokedAt != null ||
                            convite.expiresAt.toInstant(TimeZone.UTC) <= agora
                        montarPreview(grupo, user.id, agora, forcarBloqueio = if (vencido) JoinBlock.CONVITE_INVALIDO else null)
                    }
                }
            }

            if (code.isNullOrBlank()) return@flatMap naoEncontrado()
            repository.findByCode(code).flatMap { grupo ->
                if (grupo == null) return@flatMap naoEncontrado()
                montarPreview(grupo, user.id, agora, forcarBloqueio = null)
            }
        }

    /** Entrar pelo CÓDIGO digitado. */
    suspend fun entrarPorCodigo(firebaseUid: String, email: String?, code: String): AppResult<GroupDto> =
        userService.findOrCreate(firebaseUid, email).flatMap { user ->
            repository.findByCode(code).flatMap { grupo ->
                if (grupo == null) return@flatMap naoEncontrado()
                entrar(grupo, user.id, bloqueioExtra = null)
            }
        }

    /** Entrar pelo LINK. O token vencido ou revogado é bloqueio próprio, não "não existe". */
    suspend fun entrarPorConvite(firebaseUid: String, email: String?, inviteToken: String): AppResult<GroupDto> =
        userService.findOrCreate(firebaseUid, email).flatMap { user ->
            val token = runCatching { Uuid.parse(inviteToken) }.getOrNull()
                ?: return@flatMap naoEncontrado()
            repository.findInvite(token).flatMap { convite ->
                if (convite == null) return@flatMap naoEncontrado()
                repository.findById(convite.groupId).flatMap { grupo ->
                    if (grupo == null) return@flatMap naoEncontrado()
                    val vencido = convite.revokedAt != null ||
                        convite.expiresAt.toInstant(TimeZone.UTC) <= clock.now()
                    entrar(grupo, user.id, bloqueioExtra = if (vencido) JoinBlock.CONVITE_INVALIDO else null)
                }
            }
        }

    /**
     * SAIR (2.16 — a qualquer momento, inclusive com o grupo ativo).
     *
     * O admin só sai depois de transferir o cargo (2.5). A alternativa — promover alguém
     * automaticamente — decidiria pelo grupo quem manda, e essa é a decisão mais política que
     * existe ali dentro.
     */
    suspend fun sair(firebaseUid: String, email: String?, groupId: String): AppResult<Unit> =
        comMembro(firebaseUid, email, groupId) { id, user, papel ->
            if (papel == MemberRole.ADMIN) {
                return@comMembro AppError.Conflict(
                    "Transfira o cargo de admin antes de sair do grupo.",
                ).asFailure()
            }
            repository.leave(id, user)
        }

    /** EXPULSAR — só o admin (2.4), e ninguém expulsa a si mesmo (para isso existe o sair). */
    suspend fun expulsar(
        firebaseUid: String,
        email: String?,
        groupId: String,
        alvoId: String,
    ): AppResult<Unit> =
        comMembro(firebaseUid, email, groupId) { id, user, papel ->
            if (papel != MemberRole.ADMIN) return@comMembro semPermissao()
            val alvo = runCatching { Uuid.parse(alvoId) }.getOrNull() ?: return@comMembro naoEncontrado()
            if (alvo == user) {
                return@comMembro AppError.Validation("Para sair do grupo, use a opção de sair.").asFailure()
            }
            repository.roleOf(id, alvo).flatMap { papelDoAlvo ->
                if (papelDoAlvo == null) return@flatMap naoEncontrado()
                repository.leave(id, alvo)
            }
        }

    /**
     * TRANSFERIR ADMIN. Vira `MEMBRO` quem transfere, na mesma operação lógica.
     *
     * Ordem: promove o novo ANTES de rebaixar o antigo. Se a segunda escrita falhasse, o grupo
     * ficaria com dois admins — ruim, mas recuperável. Na ordem inversa ficaria com NENHUM, que
     * é o estado que a regra do admin fantasma (2.12) existe para tratar e que não queremos
     * criar por bug.
     */
    suspend fun transferirAdmin(
        firebaseUid: String,
        email: String?,
        groupId: String,
        novoAdminId: String,
    ): AppResult<Unit> =
        comMembro(firebaseUid, email, groupId) { id, user, papel ->
            if (papel != MemberRole.ADMIN) return@comMembro semPermissao()
            val novo = runCatching { Uuid.parse(novoAdminId) }.getOrNull() ?: return@comMembro naoEncontrado()
            if (novo == user) return@comMembro AppError.Validation("Você já é o admin.").asFailure()

            repository.roleOf(id, novo).flatMap { papelDoNovo ->
                if (papelDoNovo == null) return@flatMap naoEncontrado()
                repository.setRole(id, novo, MemberRole.ADMIN.name).flatMap {
                    repository.setRole(id, user, MemberRole.MEMBRO.name)
                }
            }
        }

    /** Gera (ou regenera) o link de convite. Só admin. Regenerar REVOGA o anterior. */
    suspend fun gerarConvite(firebaseUid: String, email: String?, groupId: String): AppResult<GroupInviteDto> =
        comMembro(firebaseUid, email, groupId) { id, user, papel ->
            if (papel != MemberRole.ADMIN) return@comMembro semPermissao()
            repository.findById(id).flatMap { grupo ->
                if (grupo == null) return@flatMap naoEncontrado()
                val agora = clock.now()
                val validade = GroupPolicy.validadeDoConvite(agora, grupo.startDate, grupo.timezone)
                if (validade <= agora) {
                    // O grupo já começou: gerar link que nasce vencido enganaria o admin.
                    return@flatMap AppError.Conflict("O desafio já começou — a entrada está fechada.").asFailure()
                }
                val token = Uuid.random()
                repository.createInvite(
                    token = token,
                    groupId = id,
                    createdBy = user,
                    expiresAt = validade.toLocalDateTime(TimeZone.UTC),
                    agora = agora.toLocalDateTime(TimeZone.UTC),
                ).flatMap {
                    GroupInviteDto(token = token.toString(), expiresAt = validade.toString()).asSuccess()
                }
            }
        }

    /** Revoga o link ativo. Só admin. O CÓDIGO do grupo continua valendo — são portas diferentes. */
    suspend fun revogarConvite(firebaseUid: String, email: String?, groupId: String): AppResult<Unit> =
        comMembro(firebaseUid, email, groupId) { id, user, papel ->
            if (papel != MemberRole.ADMIN) return@comMembro semPermissao()
            repository.revokeInvites(id, clock.now().toLocalDateTime(TimeZone.UTC))
        }

    // ---- interno ----

    private suspend fun entrar(grupo: Group, userId: Uuid, bloqueioExtra: JoinBlock?): AppResult<GroupDto> {
        val agora = clock.now()
        return repository.roleOf(grupo.id, userId).flatMap { papel ->
            val estado = GroupPolicy.estado(grupo.startDate, grupo.endDate, agora, grupo.timezone)
            val impedimento = bloqueioExtra
                ?: GroupPolicy.impedimentoParaEntrar(estado, grupo.memberCount, papel != null)

            // Já ser membro não é erro: quem toca duas vezes no link recebe o grupo, não uma
            // recusa. A idempotência do `join` cobre a corrida; isto cobre o caminho comum.
            if (impedimento == JoinBlock.JA_E_MEMBRO) {
                return@flatMap AppResult.Success(grupo.toDto(agora, papel.paraPapel()))
            }
            if (impedimento != null) {
                return@flatMap AppError.Conflict(impedimento.name).asFailure()
            }
            repository.join(grupo.id, userId).flatMap {
                // Relê para o memberCount sair certo: o grupo em mãos foi lido ANTES da entrada.
                repository.findById(grupo.id).flatMap { atualizado ->
                    (atualizado ?: grupo).toDto(agora, MemberRole.MEMBRO).asSuccess()
                }
            }
        }
    }

    private suspend fun montarPreview(
        grupo: Group,
        userId: Uuid,
        agora: Instant,
        forcarBloqueio: JoinBlock?,
    ): AppResult<GroupPreviewDto> =
        repository.roleOf(grupo.id, userId).flatMap { papel ->
            val estado = GroupPolicy.estado(grupo.startDate, grupo.endDate, agora, grupo.timezone)
            val impedimento = when {
                // Ser membro VENCE o convite vencido: quem já está dentro vê "você já participa",
                // não "link inválido" — o link só importa para quem está de fora.
                papel != null -> JoinBlock.JA_E_MEMBRO
                forcarBloqueio != null -> forcarBloqueio
                else -> GroupPolicy.impedimentoParaEntrar(estado, grupo.memberCount, jaEMembro = false)
            }

            GroupPreviewDto(
                id = grupo.id.toString(),
                title = grupo.title,
                description = grupo.description,
                bannerUrl = grupo.bannerUrl,
                memberCount = grupo.memberCount,
                startDate = grupo.startDate.toString(),
                endDate = grupo.endDate.toString(),
                timezone = grupo.timezone.id,
                rules = grupo.rules.sortedBy { it.name },
                state = estado,
                joinable = impedimento == null,
                blockedReason = impedimento,
            ).asSuccess()
        }

    /**
     * Resolve usuário + grupo + papel, e recusa quem não é membro com **NotFound**.
     *
     * Forbidden confirmaria que o grupo existe (ver `GroupService.porId`). Todas as ações desta
     * classe passam por aqui, para que essa decisão exista num lugar só.
     */
    private suspend fun <T> comMembro(
        firebaseUid: String,
        email: String?,
        groupId: String,
        bloco: suspend (Uuid, Uuid, MemberRole) -> AppResult<T>,
    ): AppResult<T> =
        userService.findOrCreate(firebaseUid, email).flatMap { user ->
            val id = runCatching { Uuid.parse(groupId) }.getOrNull() ?: return@flatMap naoEncontrado()
            repository.roleOf(id, user.id).flatMap { papel ->
                if (papel == null) return@flatMap naoEncontrado()
                bloco(id, user.id, papel.paraPapel())
            }
        }

    private fun <T> naoEncontrado(): AppResult<T> = AppError.NotFound("Grupo não encontrado").asFailure()
    private fun <T> semPermissao(): AppResult<T> = AppError.Forbidden("Só o admin do grupo pode fazer isso.").asFailure()

    private fun String?.paraPapel(): MemberRole =
        runCatching { MemberRole.valueOf(this!!) }.getOrDefault(MemberRole.MEMBRO)
}
