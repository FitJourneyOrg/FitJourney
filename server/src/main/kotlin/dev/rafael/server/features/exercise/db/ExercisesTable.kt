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

    // --- taxonomia (V8) ---
    val modality = varchar("modality", 16).nullable()
    val movementPattern = varchar("movement_pattern", 24).nullable()
    val secondaryPattern = varchar("secondary_pattern", 24).nullable()
    val isCompound = bool("is_compound").nullable()
    val equipment = varchar("equipment", 24).nullable()
    val primaryMuscles = array<String>("primary_muscles").nullable()
    val secondaryMuscles = array<String>("secondary_muscles").nullable()
    val unilateral = bool("unilateral").nullable()
    val prescriptionType = varchar("prescription_type", 12).nullable()
    val level = varchar("level", 16).nullable()
    val contraindications = array<String>("contraindications").nullable()

    override val primaryKey = PrimaryKey(id)
}