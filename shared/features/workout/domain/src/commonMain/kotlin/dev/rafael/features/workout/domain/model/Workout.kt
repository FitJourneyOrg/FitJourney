package dev.rafael.features.workout.domain.model

data class Workout(
    val id: String?,                      // null ao criar; server gera
    val name: String,
    val exercises: List<WorkoutExercise>,
    val createdAt: String?,
    val updatedAt: String?,
)

data class WorkoutExercise(
    val exerciseId: String,
    val orderIndex: Int,
    val restSeconds: Int,   // <- NOVO
    val sets: List<WorkoutSet>,
)

data class WorkoutSet(
    val reps: Int,
    val orderIndex: Int,
)