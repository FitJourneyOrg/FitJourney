package dev.rafael.server.features.session.models

import kotlinx.datetime.LocalDateTime
import kotlin.uuid.Uuid

data class SetLog(
    val exerciseId: Uuid,
    val orderIndex: Int,
    val setIndex: Int,
    val targetReps: Int,
    val repsDone: Int,
    val weightKg: Double?,
    val done: Boolean,
)

/** Sessão executada como o server a conhece (snapshot auto-contido). */
data class WorkoutSession(
    val id: Uuid,
    val userId: Uuid,
    val programId: Uuid?,
    val workoutId: Uuid?,
    val workoutName: String,
    val startedAt: LocalDateTime,
    val finishedAt: LocalDateTime,
    val sets: List<SetLog>,
)
