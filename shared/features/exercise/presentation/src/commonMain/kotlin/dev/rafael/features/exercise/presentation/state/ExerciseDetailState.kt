package dev.rafael.features.exercise.presentation.state

import dev.rafael.features.exercise.domain.model.Exercise

data class ExerciseDetailState(
    val exercise: Exercise? = null,
    val isLoading: Boolean = true,
    val error: String? = null,
)
