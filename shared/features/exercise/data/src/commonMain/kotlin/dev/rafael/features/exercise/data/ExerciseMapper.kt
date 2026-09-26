package dev.rafael.features.exercise.data

import dev.rafael.contract.exercise.ExerciseCategory
import dev.rafael.contract.exercise.ExerciseDto
import dev.rafael.contract.profile.MuscleGroup
import dev.rafael.features.exercise.domain.model.Exercise
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
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

private val json = Json { ignoreUnknownKeys = true }
private val musculoSerializer = ListSerializer(MuscleGroup.serializer())

/**
 * Decodifica uma coluna JSON de List<MuscleGroup> (ver 5.sqm). `null` (linha nunca sincronizada
 * por esta fatia) e JSON inválido caem no mesmo lugar: lista vazia, nunca exceção — um cache
 * ruim não pode derrubar a tela, só perder o filtro por músculo até o próximo refresh().
 */
private fun String?.toMuscleList(): List<MuscleGroup> =
    this?.let { runCatching { json.decodeFromString(musculoSerializer, it) }.getOrDefault(emptyList()) }
        ?: emptyList()

/**
 * Retorna null se a categoria do cache não existir no enum (drift contract↔client).
 *
 * `primaryMuscles`/`secondaryMuscles` vêm do cache local desde a 5.sqm — os demais campos de
 * taxonomia (equipment, movementPattern, isCompound, unilateral, prescriptionType, level)
 * continuam null aqui: o cache não os guarda, e ninguém pediu filtro por eles ainda. O detalhe
 * (getDetail) busca da rede quando precisa deles.
 */
fun ExerciseRow.toDomainOrNull(): Exercise? {
    val cat = runCatching { ExerciseCategory.valueOf(category) }.getOrNull() ?: return null
    return Exercise(
        id = id, name = name, category = cat,
        description = description, videoRef = videoRef, thumbRef = thumbRef,
        primaryMuscles = primaryMuscles.toMuscleList(),
        secondaryMuscles = secondaryMuscles.toMuscleList(),
        equipment = null, movementPattern = null,
        isCompound = null, unilateral = null,
        prescriptionType = null, level = null,
    )
}