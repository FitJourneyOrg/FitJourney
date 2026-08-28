package dev.rafael.server.features.user.db

import org.jetbrains.exposed.v1.core.Table

/** Espelha V1__create_users.sql (+ V35). firebase_uid = cola com a auth (índice único); PK é UUID interno. */
object UsersTable : Table("users") {
    val id = uuid("id")
    val firebaseUid = varchar("firebase_uid", 128).uniqueIndex()
    val email = varchar("email", 320).nullable()
    val isPremium = bool("is_premium").default(false)
    /**
     * V35. NOT NULL e sem `.default()` de propósito: quem insere é obrigado a decidir o nome,
     * e quem decide é `DisplayNamePolicy`. Um default aqui deixaria passar insert sem nome.
     */
    val displayName = varchar("display_name", 30)

    /**
     * V40 (#35). CHAR(8) único — o endereço pelo qual outra pessoa te encontra.
     *
     * Sem `.default()` pela mesma razão do `displayName`: quem insere decide, e quem decide é
     * `UserCodePolicy.gerar()`. Um default aqui deixaria passar insert sem código, e o código é
     * NOT NULL no banco.
     */
    val code = varchar("code", 8)

    override val primaryKey = PrimaryKey(id)
}