package dev.rafael.server.features.exercise.models

import dev.rafael.contract.exercise.ExerciseCategory
import kotlin.uuid.Uuid

data class Exercise(
    val id: Uuid,
    val name: String,
    val category: ExerciseCategory,
    val description: String?,
    val videoRef: String,
    val thumbRef: String,
)