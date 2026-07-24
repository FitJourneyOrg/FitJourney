package dev.rafael.server.features.workout.db

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.datetime

object WorkoutsTable : Table("workouts") {
    val id = uuid("id")
    val userId = uuid("user_id")
    val name = varchar("name", 200)
    // Colunas do programa (V11): nullable — treino MANUAL não pertence a programa.
    val programId = uuid("program_id").nullable()
    val dayOfWeek = integer("day_of_week").nullable()
    val createdAt = datetime("created_at")
    val updatedAt = datetime("updated_at")
    override val primaryKey = PrimaryKey(id)
}