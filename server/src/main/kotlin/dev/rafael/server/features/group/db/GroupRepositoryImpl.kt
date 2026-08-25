package dev.rafael.server.features.group.db

import dev.rafael.contract.group.GroupRule
import dev.rafael.contract.group.GroupType
import dev.rafael.contract.group.MemberRole
import dev.rafael.contract.group.ScoringModel
import dev.rafael.core.result.AppError
import dev.rafael.core.result.AppResult
import dev.rafael.core.result.asFailure
import dev.rafael.core.result.asSuccess
import dev.rafael.server.features.group.models.Group
import dev.rafael.server.features.group.services.GroupPolicy
import dev.rafael.server.features.user.db.UsersTable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.core.notExists
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import kotlin.time.Clock
import kotlin.uuid.Uuid

class GroupRepositoryImpl : GroupRepository {

    override suspend fun create(grupo: NovoGrupo): AppResult<Group> = dbQuery {
        val agora = Clock.System.now().toLocalDateTime(TimeZone.UTC)

        // NOVA TENTATIVA em colisão de código. Com 32^6 (~1 bilhão) de combinações, colidir é
        // raro — mas "raro" num app que roda por anos é "acontece". Quem detecta é o UNIQUE do
        // banco, e não uma consulta prévia: consultar antes e inserir depois é uma corrida.
        var ultimaFalha: Throwable? = null
        repeat(TENTATIVAS_DE_CODIGO) {
            val codigo = GroupPolicy.gerarCodigo()
            val r = runCatching {
                transaction {
                    GroupsTable.insert {
                        it[id] = grupo.id
                        it[code] = codigo
                        it[type] = grupo.type.name
                        it[scoringModel] = grupo.scoringModel.name
                        it[title] = grupo.title
                        it[description] = grupo.description
                        it[startDate] = grupo.startDate
                        it[endDate] = grupo.endDate
                        it[timezone] = grupo.timezone.id
                        it[createdBy] = grupo.createdBy
                        it[createdAt] = agora
                        it[updatedAt] = agora
                    }

                    // MESMA TRANSAÇÃO: grupo sem admin não existe nem por um instante.
                    GroupMembersTable.insert {
                        it[groupId] = grupo.id
                        it[userId] = grupo.createdBy
                        it[role] = MemberRole.ADMIN.name
                        it[joinedAt] = agora
                    }

                    if (grupo.rules.isNotEmpty()) {
                        GroupRulesTable.batchInsert(grupo.rules) { regra ->
                            this[GroupRulesTable.groupId] = grupo.id
                            this[GroupRulesTable.rule] = regra.name
                        }
                    }
                }
                codigo
            }
            r.fold(
                onSuccess = { return@dbQuery montar(grupo, it) },
                onFailure = { ultimaFalha = it },
            )
        }
        throw IllegalStateException("Não foi possível gerar um código único", ultimaFalha)
    }

    override suspend fun listByMember(userId: Uuid): AppResult<List<Group>> = dbQuery {
        transaction {
            val ids = GroupMembersTable.selectAll()
                .where { GroupMembersTable.userId eq userId }
                .map { it[GroupMembersTable.groupId] }
            if (ids.isEmpty()) return@transaction emptyList()

            val regras = regrasDe(ids)
            val contagens = contagensDe(ids)
            GroupsTable.selectAll()
                .where { GroupsTable.id inList ids }
                .orderBy(GroupsTable.createdAt to SortOrder.DESC)
                .map { it.toGroup(regras[it[GroupsTable.id]].orEmpty(), contagens[it[GroupsTable.id]] ?: 0) }
        }
    }

    override suspend fun findById(groupId: Uuid): AppResult<Group?> = dbQuery {
        transaction {
            val linha = GroupsTable.selectAll()
                .where { GroupsTable.id eq groupId }
                .singleOrNull() ?: return@transaction null
            val regras = regrasDe(listOf(groupId))[groupId].orEmpty()
            linha.toGroup(regras, contagensDe(listOf(groupId))[groupId] ?: 0)
        }
    }

    override suspend fun roleOf(groupId: Uuid, userId: Uuid): AppResult<String?> = dbQuery {
        transaction {
            GroupMembersTable.selectAll()
                .where { (GroupMembersTable.groupId eq groupId) and (GroupMembersTable.userId eq userId) }
                .singleOrNull()
                ?.get(GroupMembersTable.role)
        }
    }

    // ---- fatia A.2 ----

    override suspend fun findByCode(code: String): AppResult<Group?> = dbQuery {
        transaction {
            // `uppercase()` porque ninguém digita código em maiúscula, e recusar por causa disso
            // seria hostil com quem está justamente tentando entrar.
            val linha = GroupsTable.selectAll()
                .where { GroupsTable.code eq code.trim().uppercase() }
                .singleOrNull() ?: return@transaction null
            val id = linha[GroupsTable.id]
            linha.toGroup(regrasDe(listOf(id))[id].orEmpty(), contagensDe(listOf(id))[id] ?: 0)
        }
    }

    override suspend fun join(groupId: Uuid, userId: Uuid): AppResult<Unit> = dbQuery {
        transaction {
            // IDEMPOTENTE: dois toques no botão (ou um retry de rede) não podem virar erro na
            // cara de quem já entrou. Mesma escolha do `POST /sessions` (#30).
            GroupMembersTable.insertIgnore {
                it[GroupMembersTable.groupId] = groupId
                it[GroupMembersTable.userId] = userId
                it[role] = MemberRole.MEMBRO.name
                it[joinedAt] = Clock.System.now().toLocalDateTime(TimeZone.UTC)
            }
        }
        Unit
    }

    override suspend fun leave(groupId: Uuid, userId: Uuid): AppResult<Unit> = dbQuery {
        transaction {
            // Só o VÍNCULO sai. Os check-ins ficam no histórico do grupo (2.6): apagá-los
            // reescreveria o passado de um desafio que outras pessoas disputaram.
            GroupMembersTable.deleteWhere {
                (GroupMembersTable.groupId eq groupId) and (GroupMembersTable.userId eq userId)
            }
        }
        Unit
    }

    override suspend fun deleteIfSoleMember(groupId: Uuid, userId: Uuid): AppResult<Boolean> = dbQuery {
        transaction {
            // Uma instrução só. A condição "não existe outro membro" é do próprio DELETE, e não
            // de um `if` antes dele: o grupo pode estar AGENDADO, com o código circulando, e um
            // read-then-write deixaria alguém entrar entre a contagem e a exclusão — e perder o
            // desafio no mesmo segundo em que entrou.
            //
            // O cascade de `group_members`, `group_rules` e `group_invites` é do schema (V36/V37).
            GroupsTable.deleteWhere {
                (GroupsTable.id eq groupId) and notExists(
                    GroupMembersTable.selectAll().where {
                        (GroupMembersTable.groupId eq groupId) and
                            (GroupMembersTable.userId neq userId)
                    },
                )
            } > 0
        }
    }

    override suspend fun setRole(groupId: Uuid, userId: Uuid, role: String): AppResult<Unit> = dbQuery {
        transaction {
            GroupMembersTable.update({
                (GroupMembersTable.groupId eq groupId) and (GroupMembersTable.userId eq userId)
            }) { it[GroupMembersTable.role] = role }
        }
        Unit
    }

    override suspend fun members(groupId: Uuid): AppResult<List<GroupMemberRow>> = dbQuery {
        transaction {
            (GroupMembersTable innerJoin UsersTable)
                .selectAll()
                .where { GroupMembersTable.groupId eq groupId }
                .orderBy(GroupMembersTable.joinedAt to SortOrder.ASC)   // mais antigo primeiro (2.12)
                .map {
                    GroupMemberRow(
                        userId = it[GroupMembersTable.userId],
                        displayName = it[UsersTable.displayName],
                        role = it[GroupMembersTable.role],
                        joinedAt = it[GroupMembersTable.joinedAt],
                    )
                }
        }
    }

    override suspend fun createInvite(
        token: Uuid,
        groupId: Uuid,
        createdBy: Uuid,
        expiresAt: LocalDateTime,
        agora: LocalDateTime,
    ): AppResult<Unit> = dbQuery {
        transaction {
            // Revoga o anterior na MESMA transação: um convite ativo por grupo. Se fossem duas
            // operações, uma falha no meio deixaria dois links válidos — e "revogar o link"
            // deixaria de significar o que promete.
            revogarAtivos(groupId, agora)
            GroupInvitesTable.insert {
                it[GroupInvitesTable.token] = token
                it[GroupInvitesTable.groupId] = groupId
                it[GroupInvitesTable.createdBy] = createdBy
                it[createdAt] = agora
                it[GroupInvitesTable.expiresAt] = expiresAt
            }
        }
        Unit
    }

    override suspend fun findInvite(token: Uuid): AppResult<InviteRow?> = dbQuery {
        transaction {
            GroupInvitesTable.selectAll()
                .where { GroupInvitesTable.token eq token }
                .singleOrNull()
                ?.toInvite()
        }
    }

    override suspend fun activeInvite(groupId: Uuid): AppResult<InviteRow?> = dbQuery {
        transaction {
            GroupInvitesTable.selectAll()
                .where { (GroupInvitesTable.groupId eq groupId) and GroupInvitesTable.revokedAt.isNull() }
                .orderBy(GroupInvitesTable.createdAt to SortOrder.DESC)
                .firstOrNull()
                ?.toInvite()
        }
    }

    override suspend fun revokeInvites(groupId: Uuid, agora: LocalDateTime): AppResult<Unit> = dbQuery {
        transaction { revogarAtivos(groupId, agora) }
        Unit
    }

    private fun revogarAtivos(groupId: Uuid, agora: LocalDateTime) {
        GroupInvitesTable.update({
            (GroupInvitesTable.groupId eq groupId) and GroupInvitesTable.revokedAt.isNull()
        }) { it[revokedAt] = agora }
    }

    /**
     * Regras e contagens vêm em UMA consulta para a lista inteira, não uma por grupo. Com 5
     * grupos seriam 11 consultas — o N+1 clássico, e a lista de grupos é a primeira tela da aba.
     */
    private fun regrasDe(ids: List<Uuid>): Map<Uuid, Set<GroupRule>> =
        GroupRulesTable.selectAll()
            .where { GroupRulesTable.groupId inList ids }
            .groupBy({ it[GroupRulesTable.groupId] }) { it[GroupRulesTable.rule].paraRegra() }
            .mapValues { (_, v) -> v.filterNotNull().toSet() }

    private fun contagensDe(ids: List<Uuid>): Map<Uuid, Int> =
        GroupMembersTable.selectAll()
            .where { GroupMembersTable.groupId inList ids }
            .groupBy { it[GroupMembersTable.groupId] }
            .mapValues { (_, linhas) -> linhas.size }

    private fun montar(novo: NovoGrupo, codigo: String) = Group(
        id = novo.id,
        code = codigo,
        type = novo.type,
        scoringModel = novo.scoringModel,
        title = novo.title,
        description = novo.description,
        startDate = novo.startDate,
        endDate = novo.endDate,
        timezone = novo.timezone,
        rules = novo.rules,
        bannerUrl = null,
        createdBy = novo.createdBy,
        memberCount = 1,   // o criador, que acabou de entrar como ADMIN
    )

    private suspend fun <T> dbQuery(block: () -> T): AppResult<T> =
        withContext(Dispatchers.IO) {
            runCatching { block() }.fold(
                onSuccess = { it.asSuccess() },
                onFailure = { AppError.Unexpected("Erro de banco", it).asFailure() },
            )
        }

    private companion object {
        const val TENTATIVAS_DE_CODIGO = 5
    }
}

/**
 * Valor gravado que não existe mais no enum — regra removida numa versão futura. Ignorar em
 * silêncio é melhor que estourar: a linha órfã não faz mal, e derrubar a tela de grupos por
 * causa dela seria desproporcional. Mesma decisão do `achievement_id` (#32).
 */
private fun String.paraRegra(): GroupRule? = runCatching { GroupRule.valueOf(this) }.getOrNull()

private fun ResultRow.toInvite() = InviteRow(
    token = this[GroupInvitesTable.token],
    groupId = this[GroupInvitesTable.groupId],
    expiresAt = this[GroupInvitesTable.expiresAt],
    revokedAt = this[GroupInvitesTable.revokedAt],
)

private fun ResultRow.toGroup(regras: Set<GroupRule>, membros: Int) = Group(
    id = this[GroupsTable.id],
    code = this[GroupsTable.code],
    type = GroupType.valueOf(this[GroupsTable.type]),
    scoringModel = ScoringModel.valueOf(this[GroupsTable.scoringModel]),
    title = this[GroupsTable.title],
    description = this[GroupsTable.description],
    startDate = this[GroupsTable.startDate],
    endDate = this[GroupsTable.endDate],
    timezone = TimeZone.of(this[GroupsTable.timezone]),
    rules = regras,
    bannerUrl = this[GroupsTable.bannerUrl],
    createdBy = this[GroupsTable.createdBy],
    memberCount = membros,
)
