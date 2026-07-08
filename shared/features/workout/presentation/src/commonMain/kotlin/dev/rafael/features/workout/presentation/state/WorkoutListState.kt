package dev.rafael.features.workout.presentation.state

import dev.rafael.features.workout.domain.model.WorkoutSummary

data class WorkoutListState(
    val workouts: List<WorkoutSummary> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
)