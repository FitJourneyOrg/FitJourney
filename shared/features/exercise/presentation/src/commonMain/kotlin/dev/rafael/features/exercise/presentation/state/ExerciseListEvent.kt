package dev.rafael.features.exercise.presentation.state

import dev.rafael.contract.exercise.ExerciseCategory

sealed interface ExerciseListEvent {
    data class CategorySelected(val category: ExerciseCategory?) : ExerciseListEvent
    data object Refresh : ExerciseListEvent
}