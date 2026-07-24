package dev.rafael.server.features.program.models

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.datetime

object ProgramsTable : Table("programs") {
    val id = uuid("id")
    val userId = uuid("user_id")
    val name = varchar("name", 100)          // ARCH #26 — auto-gerado, editável via PUT /programs/{id}
    val origin = varchar("origin", 16)       // WorkoutOrigin.name — AI (motor) ou MANUAL (shell pra treino avulso)
    val daysPerWeek = integer("days_per_week")
    val split = varchar("split", 64)
    val rationale = text("rationale")
    val locked = bool("locked").default(false)
    val createdAt = datetime("created_at")
    val updatedAt = datetime("updated_at")

    override val primaryKey = PrimaryKey(id)
}
