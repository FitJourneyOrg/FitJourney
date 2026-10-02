package dev.rafael.contract.workout

import kotlinx.serialization.Serializable

/**
 * Resumo pra lista de treinos (GET /workouts). Sem a árvore completa.
 *
 * `isActive`: sempre false nesta rota (V60) -- este resumo não carrega programId/dayOfWeek,
 * então o servidor não deriva "é o de hoje" aqui, e o cliente nunca leu este campo desta rota
 * de qualquer forma (ver WorkoutMapper.toDomain). GET /programs e GET /workouts/{id} são a
 * fonte real do treino ativo do dia (ver ProgramActiveWorkout/WorkoutService.get).
 */
@Serializable
data class WorkoutSummaryDto(
    val id: String,
    val name: String,
    val exerciseCount: Int,
    val updatedAt: String,
    val isActive: Boolean = false,
)