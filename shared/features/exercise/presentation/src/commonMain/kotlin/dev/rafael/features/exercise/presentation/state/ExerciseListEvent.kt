package dev.rafael.features.exercise.presentation.state

import dev.rafael.contract.exercise.ExerciseCategory
import dev.rafael.contract.profile.MuscleGroup

sealed interface ExerciseListEvent {
    data class CategorySelected(val category: ExerciseCategory?) : ExerciseListEvent
    data class MuscleGroupSelected(val muscleGroup: MuscleGroup?) : ExerciseListEvent
    data object Refresh : ExerciseListEvent
}