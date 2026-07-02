package dev.rafael.contract.workout

import kotlinx.serialization.Serializable

/** Resumo pra lista de treinos (GET /workouts). Sem a árvore completa. */
@Serializable
data class WorkoutSummaryDto(
    val id: String,
    val name: String,
    val exerciseCount: Int,
    val updatedAt: String,
)