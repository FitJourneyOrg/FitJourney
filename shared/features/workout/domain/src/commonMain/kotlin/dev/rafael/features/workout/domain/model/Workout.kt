package dev.rafael.features.workout.domain.model

data class Workout(
    val id: String?,                      // null ao criar; server gera
    val name: String,
    val exercises: List<WorkoutExercise>,
    val createdAt: String?,
    val updatedAt: String?,
)

data class WorkoutExercise(
    val exerciseId: String,               // FK catálogo (resolvido na C.3.2)
    val orderIndex: Int,
    val sets: List<WorkoutSet>,
)

data class WorkoutSet(
    val reps: Int,
    val orderIndex: Int,
)