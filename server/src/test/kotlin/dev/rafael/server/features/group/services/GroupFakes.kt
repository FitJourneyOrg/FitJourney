package dev.rafael.server.features.group.services

import dev.rafael.contract.group.GroupRule
import dev.rafael.contract.group.GroupType
import dev.rafael.contract.group.MemberRole
import dev.rafael.contract.group.ScoringModel
import dev.rafael.core.result.AppResult
import dev.rafael.core.result.asSuccess
import dev.rafael.server.features.group.db.GroupMemberRow
import dev.rafael.server.features.group.db.GroupRepository
import dev.rafael.server.features.group.db.InviteRow
import dev.rafael.server.features.group.db.NovoGrupo
import dev.rafael.server.features.group.models.Group
import dev.rafael.server.features.user.db.UserRepository
import dev.rafael.server.features.user.models.User
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlin.uuid.Uuid

/**
 * Dublês de grupo, EM MEMÓRIA e com comportamento — não stubs que devolvem constante.
 *
 * A diferença importa para o que estes testes provam. A regra 2.5-A ("admin sozinho sai e o
 * desafio vai junto") depende de o repositório recusar a exclusão quando existe outro membro; um
 * stub que sempre devolvesse `true` faria o teste passar sem testar nada. Aqui o
 * [FakeGroupRepository.deleteIfSoleMember] implementa a mesma condição que o `DELETE` do Postgres,
 * então o service é exercitado contra a regra de verdade.
 *
 * O que este dublê NÃO cobre: a atomicidade. O `notExists` real acontece dentro de uma instrução
 * SQL; aqui é código sequencial. Por isso a corrida é testada com um gancho explícito
 * ([FakeGroupRepository.antesDeExcluir]) em vez de concorrência de mentira.
 */
class FakeGroupRepository : GroupRepository {

    private val grupos = mutableMapOf<Uuid, Group>()
    private val membros = mutableMapOf<Uuid, MutableMap<Uuid, MutableRow>>()
    private val convites = mutableMapOf<Uuid, InviteRow>()

    /**
     * Roda ANTES de avaliar a exclusão. É como se encena a corrida do `AGENDADO`: alguém entra
     * pelo código no intervalo entre o pedido de sair e a decisão do banco.
     */
    var antesDeExcluir: (() -> Unit)? = null

    private data class MutableRow(var role: String, val joinedAt: LocalDateTime)

    fun existe(groupId: Uuid): Boolean = groupId in grupos
    fun papel(groupId: Uuid, userId: Uuid): String? = membros[groupId]?.get(userId)?.role
    fun quantidadeDeMembros(groupId: Uuid): Int = membros[groupId]?.size ?: 0

    /** Monta um grupo já pronto, com o admin dentro. Devolve o id. */
    fun semear(
        admin: Uuid,
        outros: List<Uuid> = emptyList(),
        inicio: LocalDate = LocalDate(2026, 12, 1),
        fim: LocalDate = LocalDate(2026, 12, 31),
        code: String = "ABC123",
        regras: Set<GroupRule> = emptySet(),
    ): Uuid {
        val id = Uuid.random()
        grupos[id] = Group(
            id = id,
            code = code,
            type = GroupType.DESAFIO,
            scoringModel = ScoringModel.CONTAGEM_CHECKINS,
            title = "Desafio",
            description = null,
            startDate = inicio,
            endDate = fim,
            timezone = TimeZone.of("America/Sao_Paulo"),
            rules = regras,
            bannerUrl = null,
            createdBy = admin,
            memberCount = 1 + outros.size,
        )
        val linhas = mutableMapOf<Uuid, MutableRow>()
        linhas[admin] = MutableRow(MemberRole.ADMIN.name, LocalDateTime(2026, 1, 1, 0, 0))
        outros.forEachIndexed { i, u ->
            linhas[u] = MutableRow(MemberRole.MEMBRO.name, LocalDateTime(2026, 1, 2 + i, 0, 0))
        }
        membros[id] = linhas
        return id
    }

    override suspend fun create(grupo: NovoGrupo): AppResult<Group> {
        val g = Group(
            id = grupo.id,
            code = "GERADO",
            type = grupo.type,
            scoringModel = grupo.scoringModel,
            title = grupo.title,
            description = grupo.description,
            startDate = grupo.startDate,
            endDate = grupo.endDate,
            timezone = grupo.timezone,
            rules = grupo.rules,
            bannerUrl = null,
            createdBy = grupo.createdBy,
            memberCount = 1,
        )
        grupos[g.id] = g
        membros[g.id] = mutableMapOf(
            grupo.createdBy to MutableRow(MemberRole.ADMIN.name, LocalDateTime(2026, 1, 1, 0, 0)),
        )
        return g.asSuccess()
    }

    override suspend fun listByMember(userId: Uuid): AppResult<List<Group>> =
        grupos.values.filter { membros[it.id]?.containsKey(userId) == true }.map(::comContagem).asSuccess()

    override suspend fun findById(groupId: Uuid): AppResult<Group?> =
        grupos[groupId]?.let(::comContagem).asSuccess()

    override suspend fun roleOf(groupId: Uuid, userId: Uuid): AppResult<String?> =
        membros[groupId]?.get(userId)?.role.asSuccess()

    /** CONTA as chamadas: é assim que o teste prova que a lista não faz uma consulta por grupo. */
    var chamadasDeRolesOf = 0
        private set

    override suspend fun rolesOf(groupIds: List<Uuid>, userId: Uuid): AppResult<Map<Uuid, String>> {
        chamadasDeRolesOf++
        return groupIds.mapNotNull { g -> membros[g]?.get(userId)?.role?.let { g to it } }
            .toMap()
            .asSuccess()
    }

    override suspend fun findByCode(code: String): AppResult<Group?> =
        grupos.values.firstOrNull { it.code.equals(code, ignoreCase = true) }?.let(::comContagem).asSuccess()

    override suspend fun join(groupId: Uuid, userId: Uuid): AppResult<Unit> {
        membros.getOrPut(groupId) { mutableMapOf() }
            .putIfAbsent(userId, MutableRow(MemberRole.MEMBRO.name, LocalDateTime(2026, 6, 1, 0, 0)))
        return Unit.asSuccess()
    }

    override suspend fun leave(groupId: Uuid, userId: Uuid): AppResult<Unit> {
        membros[groupId]?.remove(userId)
        return Unit.asSuccess()
    }

    override suspend fun deleteIfSoleMember(groupId: Uuid, userId: Uuid): AppResult<Boolean> {
        antesDeExcluir?.invoke()
        val temOutro = membros[groupId].orEmpty().keys.any { it != userId }
        if (temOutro) return false.asSuccess()
        grupos.remove(groupId)
        membros.remove(groupId)          // o cascade do schema, em memória
        convites.values.removeAll { it.groupId == groupId }
        return true.asSuccess()
    }

    override suspend fun setRole(groupId: Uuid, userId: Uuid, role: String): AppResult<Unit> {
        membros[groupId]?.get(userId)?.role = role
        return Unit.asSuccess()
    }

    override suspend fun members(groupId: Uuid): AppResult<List<GroupMemberRow>> =
        membros[groupId].orEmpty()
            .map { (id, linha) -> GroupMemberRow(id, "Pessoa ${id.toString().take(4)}", linha.role, linha.joinedAt) }
            .sortedBy { it.joinedAt }
            .asSuccess()

    override suspend fun createInvite(
        token: Uuid,
        groupId: Uuid,
        createdBy: Uuid,
        expiresAt: LocalDateTime,
        agora: LocalDateTime,
    ): AppResult<Unit> {
        convites.values.removeAll { it.groupId == groupId }
        convites[token] = InviteRow(token, groupId, expiresAt, null)
        return Unit.asSuccess()
    }

    override suspend fun findInvite(token: Uuid): AppResult<InviteRow?> = convites[token].asSuccess()

    override suspend fun activeInvite(groupId: Uuid): AppResult<InviteRow?> =
        convites.values.firstOrNull { it.groupId == groupId && it.revokedAt == null }.asSuccess()

    override suspend fun revokeInvites(groupId: Uuid, agora: LocalDateTime): AppResult<Unit> {
        convites.entries.filter { it.value.groupId == groupId }.forEach { (k, v) ->
            convites[k] = v.copy(revokedAt = agora)
        }
        return Unit.asSuccess()
    }

    /** `memberCount` é derivado da lista, como no banco — nunca um número guardado à parte. */
    private fun comContagem(g: Group): Group = g.copy(memberCount = membros[g.id]?.size ?: 0)
}

/** O mínimo para montar um `UserService` de verdade: o service não é interface. */
class FakeUserRepository(usuarios: List<User> = emptyList()) : UserRepository {
    private val porUid = usuarios.associateBy { it.firebaseUid }.toMutableMap()

    override suspend fun findByFirebaseUid(firebaseUid: String): AppResult<User?> =
        porUid[firebaseUid].asSuccess()

    override suspend fun create(
        id: Uuid,
        firebaseUid: String,
        email: String?,
        displayName: String,
    ): AppResult<User> {
        val u = User(id, firebaseUid, email, isPremium = false, displayName = displayName)
        porUid[firebaseUid] = u
        return u.asSuccess()
    }

    override suspend fun setPremium(userId: Uuid, premium: Boolean): AppResult<User?> =
        atualizar(userId) { it.copy(isPremium = premium) }

    override suspend fun updateDisplayName(userId: Uuid, displayName: String): AppResult<User?> =
        atualizar(userId) { it.copy(displayName = displayName) }

    private fun atualizar(userId: Uuid, bloco: (User) -> User): AppResult<User?> {
        val atual = porUid.values.firstOrNull { it.id == userId } ?: return null.asSuccess()
        val novo = bloco(atual)
        porUid[novo.firebaseUid] = novo
        return novo.asSuccess()
    }
}

fun usuario(uid: String, id: Uuid = Uuid.random()) =
    User(id = id, firebaseUid = uid, email = "$uid@x.com", isPremium = false, displayName = uid)
