package dev.rafael.features.profile.domain.model

import dev.rafael.contract.profile.Goal
import dev.rafael.contract.profile.Level
import dev.rafael.contract.profile.MuscleGroup

data class Profile(
    val goal: Goal,
    val level: Level,
    val daysPerWeek: Int,
    val focusAreas: List<MuscleGroup>,
    val weightKg: Double?,
    val heightCm: Double?,
    val onboardingCompleted: Boolean,
)