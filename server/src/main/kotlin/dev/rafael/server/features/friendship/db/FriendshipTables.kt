package dev.rafael.server.features.friendship.db

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.datetime

/**
 * Espelha `friendships` da V40.
 *
 * `userA` é sempre o MENOR uuid do par — invariante da tabela, garantido por CHECK no banco e
 * produzido por `FriendshipPolicy.par()`. É o que faz a PK impedir pedido cruzado.
 */
object FriendshipsTable : Table("friendships") {
    val userA = uuid("user_a")
    val userB = uuid("user_b")

    /** Quem MANDOU. Some da chave por causa da ordem canônica, então precisa existir à parte. */
    val requestedBy = uuid("requested_by")

    val status = varchar("status", 16)
    val createdAt = datetime("created_at")
    val respondedAt = datetime("responded_at").nullable()

    override val primaryKey = PrimaryKey(userA, userB)
}

/**
 * Espelha `blocks` da V40. **Direcional**: uma linha por sentido.
 *
 * Tabela separada de [FriendshipsTable] porque amizade é simétrica e bloqueio não é — juntas,
 * "estes dois são o quê?" dependeria de qual linha foi lida primeiro (emenda 35.2).
 */
object BlocksTable : Table("blocks") {
    val blockerId = uuid("blocker_id")
    val blockedId = uuid("blocked_id")
    val createdAt = datetime("created_at")

    override val primaryKey = PrimaryKey(blockerId, blockedId)
}
