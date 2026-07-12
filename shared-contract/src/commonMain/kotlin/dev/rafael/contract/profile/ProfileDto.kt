package dev.rafael.contract.profile

import kotlinx.serialization.Serializable

/**
 * Perfil de onboarding. Obrigatórios: goal, level, daysPerWeek.
 * Opcionais (puláveis): focusAreas (0-2), weightKg, heightCm.
 * onboardingCompleted é derivado no server (true quando os obrigatórios estão preenchidos).
 */
@Serializable
data class ProfileDto(
    val goal: Goal,
    val level: Level,
    val daysPerWeek: Int,
    val focusAreas: List<MuscleGroup> = emptyList(),
    val weightKg: Double? = null,
    val heightCm: Double? = null,
    val environment: TrainingEnvironment? = null,
    val health: HealthScreening? = null,
    val onboardingCompleted: Boolean = false,
)