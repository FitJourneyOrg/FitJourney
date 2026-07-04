package dev.rafael.features.exercise.domain.model

import dev.rafael.contract.exercise.ExerciseCategory

data class Exercise(
    val id: String,
    val name: String,
    val category: ExerciseCategory,
    val description: String?,
    val videoRef: String,
    val thumbRef: String,
)