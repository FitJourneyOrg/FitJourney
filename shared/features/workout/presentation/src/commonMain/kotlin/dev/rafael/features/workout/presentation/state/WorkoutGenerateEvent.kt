package dev.rafael.features.workout.presentation.state

sealed interface WorkoutGenerateEvent {
    data class PromptChanged(val value: String) : WorkoutGenerateEvent
    data object Generate : WorkoutGenerateEvent
    data object DismissError : WorkoutGenerateEvent
}