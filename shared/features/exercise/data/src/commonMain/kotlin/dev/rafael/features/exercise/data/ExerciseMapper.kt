package dev.rafael.features.exercise.data

import dev.rafael.contract.exercise.ExerciseCategory
import dev.rafael.contract.exercise.ExerciseDto
import dev.rafael.features.exercise.domain.model.Exercise
import dev.rafael.core.database.Exercise as ExerciseRow

/** ExerciseDto (rede) → domínio. Traz a taxonomia (usado nas alternativas e no detalhe). */
fun ExerciseDto.toDomain(): Exercise = Exercise(
    id = id, name = name, category = category,
    description = description, videoRef = videoRef, thumbRef = thumbRef,
    primaryMuscles = primaryMuscles, secondaryMuscles = secondaryMuscles,
    equipment = equipment, movementPattern = movementPattern,
    isCompound = isCompound, unilateral = unilateral,
    prescriptionType = prescriptionType, level = level,
)

/** Retorna null se a categoria do cache não existir no enum (drift contract↔client). */
fun ExerciseRow.toDomainOrNull(): Exercise? {
    val cat = runCatching { ExerciseCategory.valueOf(category) }.getOrNull() ?: return null
    return Exercise(
        id = id, name = name, category = cat,
        description = description, videoRef = videoRef, thumbRef = thumbRef,
        // cache local (SQLDelight) não guarda taxonomia — o detalhe busca da rede.
        primaryMuscles = emptyList(), secondaryMuscles = emptyList(),
        equipment = null, movementPattern = null,
        isCompound = null, unilateral = null,
        prescriptionType = null, level = null,
    )
}