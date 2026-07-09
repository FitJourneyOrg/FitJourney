package dev.rafael.features.workout.presentation.state

sealed interface WorkoutDetailEvent {
    data object Retry : WorkoutDetailEvent
    data object Delete : WorkoutDetailEvent
}