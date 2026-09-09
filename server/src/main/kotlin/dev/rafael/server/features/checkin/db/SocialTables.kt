package dev.rafael.server.features.checkin.db

import dev.rafael.server.features.group.db.GroupsTable
import dev.rafael.server.features.user.db.UsersTable
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.datetime

/** Espelha `check_in_comments` da V44. */
object CheckInCommentsTable : Table("check_in_comments") {
    val id = uuid("id")
    val checkInId = uuid("check_in_id").references(CheckInsTable.id)

    /**
     * Redundante com o `check_in_id`, e de propósito: a fila de moderação da E.2 lista
     * "comentários deste grupo", e sem esta coluna cada consulta passaria por `check_ins`.
     */
    val groupId = uuid("group_id").references(GroupsTable.id)

    val userId = uuid("user_id").references(UsersTable.id)

    /** 8.1: máximo 500. O CHECK do banco usa o mesmo número que a `SocialPolicy`. */
    val body = varchar("body", 500)

    val createdAt = datetime("created_at")

    /**
     * **Não existe `updatedAt`**, e a ausência é a regra: comentário não se edita (8.1). Uma coluna
     * de edição convidaria alguém a implementá-la, e o que 49 pessoas leram deixaria de ser o que
     * está escrito.
     */

    override val primaryKey = PrimaryKey(id)
}

/**
 * Espelha `check_in_reactions` da V44.
 *
 * **A PK composta É a regra** "uma reação por pessoa por check-in" (8.2). Não há checagem no
 * Kotlin porque não precisa: o banco recusa a segunda, e trocar é `ON CONFLICT DO UPDATE`.
 */
object CheckInReactionsTable : Table("check_in_reactions") {
    val checkInId = uuid("check_in_id").references(CheckInsTable.id)
    val groupId = uuid("group_id").references(GroupsTable.id)
    val userId = uuid("user_id").references(UsersTable.id)

    /** `VARCHAR(16)`: emoji não é um caractere. Ver a V43 e a V44. */
    val emoji = varchar("emoji", 16)

    val createdAt = datetime("created_at")

    override val primaryKey = PrimaryKey(checkInId, userId)
}
