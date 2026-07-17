package dev.rafael.features.profile.domain.model

import dev.rafael.contract.profile.BodyLimitation
import dev.rafael.contract.profile.Goal
import dev.rafael.contract.profile.HealthScreening
import dev.rafael.contract.profile.Level
import dev.rafael.contract.profile.MuscleGroup
import dev.rafael.contract.profile.TrainingEnvironment

data class Profile(
    val goal: Goal,
    val level: Level,
    val daysPerWeek: Int,
    val focusAreas: List<MuscleGroup>,
    val weightKg: Double?,
    val heightCm: Double?,
    val environment: TrainingEnvironment? = null,
    val limitations: List<BodyLimitation> = emptyList(),
    val health: HealthScreening? = null,
    val onboardingCompleted: Boolean,
)