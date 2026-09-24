package dev.rafael.features.exercise.domain.model

import dev.rafael.contract.exercise.ExerciseCategory
import dev.rafael.contract.profile.Level
import dev.rafael.contract.profile.MuscleGroup

data class Exercise(
    val id: String,
    val name: String,
    val category: ExerciseCategory,
    val description: String?,
    val videoRef: String,
    val thumbRef: String,
    // Taxonomia. primaryMuscles/secondaryMuscles vêm do cache local desde a 5.sqm (filtro por
    // músculo na Biblioteca) — os demais só o caminho de rede (getDetail) preenche; o cache
    // devolve null pra eles.
    val primaryMuscles: List<MuscleGroup>,
    val secondaryMuscles: List<MuscleGroup>,
    val equipment: String?,
    val movementPattern: String?,
    val isCompound: Boolean?,
    val unilateral: Boolean?,
    val prescriptionType: String?,
    val level: Level?,
)
