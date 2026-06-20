package dev.rafael.server.features.user.db

import org.jetbrains.exposed.v1.core.Table

/** Espelha V1__create_users.sql. firebase_uid = cola com a auth (índice único); PK é UUID interno. */
object UsersTable : Table("users") {
    val id = uuid("id")
    val firebaseUid = varchar("firebase_uid", 128).uniqueIndex()
    val email = varchar("email", 320).nullable()

    override val primaryKey = PrimaryKey(id)
}