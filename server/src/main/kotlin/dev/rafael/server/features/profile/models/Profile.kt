package dev.rafael.server.features.profile.models

import dev.rafael.contract.profile.Goal
import dev.rafael.contract.profile.Level
import dev.rafael.contract.profile.MuscleGroup
import kotlin.uuid.Uuid

/** Perfil como o server o conhece. focusAreas é lista de enums (no banco vira JSON em TEXT). */
data class Profile(
    val userId: Uuid,
    val goal: Goal,
    val level: Level,
    val daysPerWeek: Int,
    val focusAreas: List<MuscleGroup>,
    val weightKg: Double?,
    val heightCm: Double?,
    val onboardingCompleted: Boolean,
)
