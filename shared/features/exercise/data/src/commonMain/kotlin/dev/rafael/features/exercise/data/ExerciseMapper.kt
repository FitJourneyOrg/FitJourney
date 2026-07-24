package dev.rafael.features.exercise.data

import dev.rafael.contract.exercise.ExerciseCategory
import dev.rafael.contract.exercise.ExerciseDto
import dev.rafael.features.exercise.domain.model.Exercise
import dev.rafael.core.database.Exercise as ExerciseRow

/** ExerciseDto (rede) → domínio. Usado nas alternativas (não passam pelo cache local). */
fun ExerciseDto.toDomain(): Exercise = Exercise(
    id = id, name = name, category = category,
    description = description, videoRef = videoRef, thumbRef = thumbRef,
)

/** Retorna null se a categoria do cache não existir no enum (drift contract↔client). */
fun ExerciseRow.toDomainOrNull(): Exercise? {
    val cat = runCatching { ExerciseCategory.valueOf(category) }.getOrNull() ?: return null
    return Exercise(
        id = id, name = name, category = cat,
        description = description, videoRef = videoRef, thumbRef = thumbRef,
    )
}