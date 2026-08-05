package dev.rafael.server.features.session.db

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.datetime

/** Espelha V20. program_id/workout_id são referência (sem FK) — snapshot auto-contido. */
object WorkoutSessionsTable : Table("workout_sessions") {
    val id = uuid("id")                                   // gerado no cliente (idempotência)
    val userId = uuid("user_id")
    val programId = uuid("program_id").nullable()
    val workoutId = uuid("workout_id").nullable()
    val workoutName = text("workout_name")
    val startedAt = datetime("started_at")                // relógio do cliente
    val finishedAt = datetime("finished_at")              // relógio do cliente
    val createdAt = datetime("created_at")                // relógio do servidor (sync)
    override val primaryKey = PrimaryKey(id)
}

object SessionSetLogsTable : Table("session_set_logs") {
    val id = uuid("id")
    val sessionId = uuid("session_id")
    val exerciseId = uuid("exercise_id")
    val orderIndex = integer("order_index")
    val setIndex = integer("set_index")
    val targetReps = integer("target_reps")
    val repsDone = integer("reps_done")
    val weightKg = double("weight_kg").nullable()
    val done = bool("done")
    override val primaryKey = PrimaryKey(id)
}
