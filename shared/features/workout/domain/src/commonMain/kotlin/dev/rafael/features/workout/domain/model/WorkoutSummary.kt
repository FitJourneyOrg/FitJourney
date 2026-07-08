package dev.rafael.features.workout.domain.model

data class WorkoutSummary(
    val id: String,
    val name: String,
    val exerciseCount: Int,
    val updatedAt: String,
)