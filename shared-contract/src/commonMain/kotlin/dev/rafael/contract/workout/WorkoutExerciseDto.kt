package dev.rafael.contract.workout

import kotlinx.serialization.Serializable

/** Um exercício dentro do treino. Referencia o catálogo por exerciseId. */
@Serializable
data class WorkoutExerciseDto(
    val exerciseId: String,          // FK → catálogo (o cliente cruza p/ nome/thumb)
    val orderIndex: Int,
    val sets: List<WorkoutSetDto>,
)