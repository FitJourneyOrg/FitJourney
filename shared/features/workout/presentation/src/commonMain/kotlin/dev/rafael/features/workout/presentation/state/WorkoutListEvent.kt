package dev.rafael.features.workout.presentation.state

sealed interface WorkoutListEvent {
    data object Load : WorkoutListEvent      // carrega/recarrega a lista
    data object Retry : WorkoutListEvent     // após erro
}