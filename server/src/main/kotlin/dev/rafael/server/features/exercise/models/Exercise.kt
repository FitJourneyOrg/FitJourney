package dev.rafael.server.features.exercise.models

import dev.rafael.contract.exercise.ExerciseCategory
import dev.rafael.contract.profile.BodyLimitation
import dev.rafael.contract.profile.Level
import dev.rafael.contract.profile.MuscleGroup
import kotlin.uuid.Uuid

data class Exercise(
    val id: Uuid,
    val name: String,
    val category: ExerciseCategory,
    val description: String?,
    val videoRef: String,
    val thumbRef: String,

    // --- taxonomia (V8) — null nos 542 não-curados ---
    val modality: Modality?,
    val movementPattern: MovementPattern?,
    val secondaryPattern: MovementPattern?,
    val isCompound: Boolean?,
    val equipment: String?,
    val primaryMuscles: List<MuscleGroup>,
    val secondaryMuscles: List<MuscleGroup>,
    val unilateral: Boolean?,
    val prescriptionType: PrescriptionType?,
    val level: Level?,
    val contraindications: List<BodyLimitation>,

    // --- V16: principal (base) vs variação. Motor escolhe base primeiro. ---
    val isBase: Boolean,
)