package dev.rafael.server.features.exercise.db

import org.jetbrains.exposed.v1.core.Table

/** Espelha V3__create_exercises.sql. Catálogo read-only de 963 exercícios. */
object ExercisesTable : Table("exercises") {
    val id = uuid("id")
    val name = varchar("name", 200)
    val category = varchar("category", 32)
    val description = text("description").nullable()
    val videoRef = varchar("video_ref", 300)
    val thumbRef = varchar("thumb_ref", 300)
    override val primaryKey = PrimaryKey(id)
}