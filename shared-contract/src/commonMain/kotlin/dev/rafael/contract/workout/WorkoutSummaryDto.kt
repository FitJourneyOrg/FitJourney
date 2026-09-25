package dev.rafael.contract.workout

import kotlinx.serialization.Serializable

/**
 * Resumo pra lista de treinos (GET /workouts). Sem a árvore completa.
 *
 * `isActive` (V59): true pro treino marcado como ativo pelo usuário (ponteiro exclusivo,
 * autoridade do servidor). Ver POST /workouts/{id}/activate.
 */
@Serializable
data class WorkoutSummaryDto(
    val id: String,
    val name: String,
    val exerciseCount: Int,
    val updatedAt: String,
    val isActive: Boolean = false,
)