package dev.rafael.features.profile.domain.model

import dev.rafael.contract.profile.BodyLimitation
import dev.rafael.contract.profile.Goal
import dev.rafael.contract.profile.HealthScreening
import dev.rafael.contract.profile.Level
import dev.rafael.contract.profile.MuscleGroup
import dev.rafael.contract.profile.SplitType
import dev.rafael.contract.profile.TrainingEnvironment

data class Profile(
    val goal: Goal,
    val level: Level,
    val daysPerWeek: Int,
    val splitPreference: SplitType? = null,   // ARCH #29
    val unavailableDays: List<Int> = emptyList(),   // dias off (1=Seg..7=Dom); motor evita
    val focusAreas: List<MuscleGroup>,
    val weightKg: Double?,
    val heightCm: Double?,
    val age: Int? = null,                  // #24
    val minorSupervised: Boolean = false,  // #24
    val environment: TrainingEnvironment? = null,
    val limitations: List<BodyLimitation> = emptyList(),
    val health: HealthScreening? = null,
    val onboardingCompleted: Boolean,
)