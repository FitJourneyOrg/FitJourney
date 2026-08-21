package dev.rafael.server.features.group.db

import dev.rafael.server.features.user.db.UsersTable
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.date
import org.jetbrains.exposed.v1.datetime.datetime

/**
 * Espelha `V36__create_groups.sql`.
 *
 * **Não há coluna `status`** — o estado é derivado por `GroupPolicy.estado()` a cada leitura.
 * Se um dia alguém sentir falta dela aqui, é sinal de que o derivado virou custo; a resposta
 * então é cache, não persistência (ver ARCH #33).
 */
object GroupsTable : Table("groups") {
    val id = uuid("id")
    val code = varchar("code", 6).uniqueIndex()

    /** TEXT no banco; o enum vive no Kotlin (contrato). Ver a migration para o porquê. */
    val type = text("type")
    val scoringModel = text("scoring_model")

    val title = varchar("title", 60)
    val description = varchar("description", 300).nullable()

    /** Dia CIVIL no [timezone] do grupo — não instante. */
    val startDate = date("start_date")
    val endDate = date("end_date")

    /** IANA (`America/Sao_Paulo`), nunca offset. */
    val timezone = text("timezone")

    val bannerUrl = text("banner_url").nullable()   // fatia A.3

    val createdBy = uuid("created_by").references(UsersTable.id)
    val createdAt = datetime("created_at")
    val updatedAt = datetime("updated_at")

    override val primaryKey = PrimaryKey(id)
}

object GroupMembersTable : Table("group_members") {
    val groupId = uuid("group_id").references(GroupsTable.id)
    val userId = uuid("user_id").references(UsersTable.id)
    val role = text("role")
    val joinedAt = datetime("joined_at")

    override val primaryKey = PrimaryKey(groupId, userId)
}

/**
 * Regras obrigatórias. Tabela filha e não coluna array: acrescentar um tipo de regra não custa
 * migration, e a fatia D lê isto a cada check-in.
 */
object GroupRulesTable : Table("group_rules") {
    val groupId = uuid("group_id").references(GroupsTable.id)
    val rule = text("rule")

    override val primaryKey = PrimaryKey(groupId, rule)
}
