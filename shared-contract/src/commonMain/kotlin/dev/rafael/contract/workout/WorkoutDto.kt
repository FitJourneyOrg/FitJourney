package dev.rafael.contract.workout

import kotlinx.serialization.Serializable

/**
 * Treino completo (aggregate). Usado no POST (criar), GET/{id} (ler), PUT (editar).
 * id/createdAt/updatedAt são nulos no POST (o servidor gera); preenchidos na resposta.
 */
@Serializable
data class WorkoutDto(
    val id: String? = null,
    val name: String,
    val origin: WorkoutOrigin = WorkoutOrigin.MANUAL,
    val exercises: List<WorkoutExerciseDto> = emptyList(),
    val createdAt: String? = null,   // ISO-8601; servidor preenche
    val updatedAt: String? = null,
)