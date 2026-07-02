package dev.rafael.contract.workout

import kotlinx.serialization.Serializable

/** Série planejada. Sem peso — o peso é da execução (Fatia E). */
@Serializable
data class WorkoutSetDto(
    val reps: Int,
    val orderIndex: Int,
)