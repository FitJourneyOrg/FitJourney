package dev.rafael.features.workout.presentation.state

sealed interface WorkoutFormEvent {
    data class NameChanged(val value: String) : WorkoutFormEvent
    data class ExercisesAdded(val ids: List<String>) : WorkoutFormEvent
    data class ExerciseRemoved(val index: Int) : WorkoutFormEvent
    data class ExerciseMovedUp(val index: Int) : WorkoutFormEvent
    data class ExerciseMovedDown(val index: Int) : WorkoutFormEvent
    data class SetAdded(val exerciseIndex: Int) : WorkoutFormEvent
    data class SetRemoved(val exerciseIndex: Int, val setIndex: Int) : WorkoutFormEvent
    data class SetRepsChanged(val exerciseIndex: Int, val setIndex: Int, val reps: String) : WorkoutFormEvent
    data object Save : WorkoutFormEvent
    data object Retry : WorkoutFormEvent
}