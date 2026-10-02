package dev.rafael.features.exercise.presentation.state

import dev.rafael.contract.exercise.ExerciseCategory
import dev.rafael.contract.profile.MuscleGroup
import dev.rafael.core.result.AppError
import dev.rafael.features.exercise.domain.model.Exercise

data class ExerciseListState(
    val exercises: List<Exercise> = emptyList(),
    val selectedCategory: ExerciseCategory? = null,   // null = todas
    val selectedMuscleGroup: MuscleGroup? = null,     // null = todos — coexiste com selectedCategory

    /**
     * O que está DIGITADO no campo, que não é o mesmo que o que está sendo consultado: o campo
     * acompanha a tecla, a consulta espera o debounce (ver `ExerciseListViewModel`). Guardar os
     * dois no mesmo lugar faria o texto engasgar enquanto se digita.
     */
    val busca: String = "",
    val isRefreshing: Boolean = false,
    val error: AppError? = null,
)