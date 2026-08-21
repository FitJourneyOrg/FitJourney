package dev.rafael.server.features.group.db

import dev.rafael.core.result.AppResult
import dev.rafael.server.features.group.models.Group
import dev.rafael.server.features.group.services.GroupPolicy
import kotlinx.datetime.LocalDateTime
import kotlin.uuid.Uuid

/** Acesso a dados de grupo (ARCH #33, fatia A.1). */
interface GroupRepository {

    /**
     * Cria o grupo E o vínculo do criador como `ADMIN`, na MESMA transação.
     *
     * Os dois juntos porque grupo sem admin não pode existir nem por um instante: se o insert
     * do membro falhasse depois, sobraria um grupo órfão que ninguém administra e que a regra
     * do admin fantasma (2.12) trataria como "conta deletada" — um estado inventado por bug.
     *
     * O CÓDIGO é sorteado aqui dentro, com nova tentativa em colisão: [GroupPolicy.gerarCodigo]
     * só sorteia, e quem garante unicidade é o `UNIQUE` do banco.
     */
    suspend fun create(grupo: NovoGrupo): AppResult<Group>

    /** Grupos de que o usuário participa. Ordem: mais recentes primeiro. */
    suspend fun listByMember(userId: Uuid): AppResult<List<Group>>

    /** Um grupo por id, ou null. Não filtra por membro — quem decide isso é o service. */
    suspend fun findById(groupId: Uuid): AppResult<Group?>

    /** Papel do usuário no grupo, ou null se não é membro. */
    suspend fun roleOf(groupId: Uuid, userId: Uuid): AppResult<String?>

    // ---- fatia A.2: entrada e papéis ----

    /** Grupo pelo CÓDIGO digitado. Case-insensitive: ninguém digita código em maiúscula. */
    suspend fun findByCode(code: String): AppResult<Group?>

    /**
     * Entra no grupo como `MEMBRO`.
     *
     * A checagem de teto e de estado é do service, mas o `insert` é **idempotente**: dois toques
     * no botão não podem virar erro na cara de quem já entrou.
     */
    suspend fun join(groupId: Uuid, userId: Uuid): AppResult<Unit>

    /** Remove o vínculo. Os check-ins ficam no histórico do grupo (2.6) — só o vínculo sai. */
    suspend fun leave(groupId: Uuid, userId: Uuid): AppResult<Unit>

    /** Troca o papel de alguém. Usado na transferência de admin (2.5). */
    suspend fun setRole(groupId: Uuid, userId: Uuid, role: String): AppResult<Unit>

    /** Membros do grupo, mais antigos primeiro — a ordem que a reivindicação de admin usa (2.12). */
    suspend fun members(groupId: Uuid): AppResult<List<GroupMemberRow>>

    // ---- convites ----

    /** Cria o link e REVOGA o anterior, na mesma transação: um convite ativo por grupo. */
    suspend fun createInvite(
        token: Uuid,
        groupId: Uuid,
        createdBy: Uuid,
        expiresAt: LocalDateTime,
        agora: LocalDateTime,
    ): AppResult<Unit>

    /** Convite pelo token, sem julgar validade — quem decide se vale é o service. */
    suspend fun findInvite(token: Uuid): AppResult<InviteRow?>

    /** Convite ATIVO do grupo (o mais recente não revogado), ou null. */
    suspend fun activeInvite(groupId: Uuid): AppResult<InviteRow?>

    /** Revoga o convite ativo do grupo. Sem convite ativo, não faz nada. */
    suspend fun revokeInvites(groupId: Uuid, agora: LocalDateTime): AppResult<Unit>
}

/** Linha de membro, crua. O `joinedAt` é o que ordena a fila da reivindicação de admin. */
data class GroupMemberRow(
    val userId: Uuid,
    val displayName: String,
    val role: String,
    val joinedAt: LocalDateTime,
)

/** Linha de convite, crua. Validade é decisão do service, não do banco. */
data class InviteRow(
    val token: Uuid,
    val groupId: Uuid,
    val expiresAt: LocalDateTime,
    val revokedAt: LocalDateTime?,
)
