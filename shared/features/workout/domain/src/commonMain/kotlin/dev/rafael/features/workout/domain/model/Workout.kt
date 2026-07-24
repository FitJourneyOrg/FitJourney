package dev.rafael.features.workout.domain.model

data class Workout(
    val id: String?,                      // null ao criar; server gera
    val name: String,
    // Obrigatório na criação (ARCH #26 — todo treino vive dentro de um programa).
    // Nullable no tipo porque também é usado pra ler treinos legados sem programa.
    val programId: String?,
    val exercises: List<WorkoutExercise>,
    val createdAt: String?,
    val updatedAt: String?,
)

data class WorkoutExercise(
    val exerciseId: String,
    val orderIndex: Int,
    val restSeconds: Int = 90,   // <- NOVO
    val sets: List<WorkoutSet>,
)

data class WorkoutSet(
    val reps: Int,
    val orderIndex: Int,
)