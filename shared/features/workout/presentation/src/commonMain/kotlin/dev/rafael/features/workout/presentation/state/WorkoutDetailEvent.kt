package dev.rafael.features.workout.presentation.state

sealed interface WorkoutDetailEvent {
    data object Retry : WorkoutDetailEvent
    data object Delete : WorkoutDetailEvent
    data class SwapExercise(val orderIndex: Int, val newExerciseId: String) : WorkoutDetailEvent
    data class RemoveExercise(val orderIndex: Int) : WorkoutDetailEvent
    data object DismissPaywall : WorkoutDetailEvent
}