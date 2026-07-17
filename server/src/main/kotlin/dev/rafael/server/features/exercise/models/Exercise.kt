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
    val modality: Modality? = null,
    val movementPattern: MovementPattern? = null,
    val secondaryPattern: MovementPattern? = null,
    val isCompound: Boolean? = null,
    val equipment: String? = null,
    val primaryMuscles: List<MuscleGroup> = emptyList(),
    val secondaryMuscles: List<MuscleGroup> = emptyList(),
    val unilateral: Boolean? = null,
    val prescriptionType: PrescriptionType? = null,
    val level: Level? = null,
    val contraindications: List<BodyLimitation> = emptyList(),
)