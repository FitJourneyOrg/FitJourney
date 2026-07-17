package dev.rafael.features.profile.presentation.state

import dev.rafael.contract.profile.BodyLimitation
import dev.rafael.contract.profile.Goal
import dev.rafael.contract.profile.HealthScreening
import dev.rafael.contract.profile.Level
import dev.rafael.contract.profile.MuscleGroup
import dev.rafael.contract.profile.TrainingEnvironment

/** Os passos do quiz, em ordem. (Equipamento sai pra Fase 4.) */
enum class QuizStep { GOAL, LEVEL, DAYS, FOCUS, BODY, ENVIRONMENT, HEALTH, LIMITATIONS }
data class QuizState(
    val step: QuizStep = QuizStep.GOAL,
    val goal: Goal? = null,
    val level: Level? = null,
    val daysPerWeek: Int? = null,
    val focusAreas: List<MuscleGroup> = emptyList(),
    val weightKg: Double? = null,
    val heightCm: Double? = null,
    val environment: TrainingEnvironment? = null,          // <- novo
    val limitations: List<BodyLimitation> = emptyList(),
    val health: HealthScreening = HealthScreening(),        // <- novo (não-null, começa tudo false)
    val isSubmitting: Boolean = false,
    val error: String? = null,
    val completed: Boolean = false,
) {


    val canAdvance: Boolean
        get() = when (step) {
            QuizStep.GOAL -> goal != null
            QuizStep.LEVEL -> level != null
            QuizStep.DAYS -> daysPerWeek != null
            QuizStep.FOCUS -> true
            QuizStep.BODY -> true
            QuizStep.ENVIRONMENT -> environment != null      // obrigatório escolher
            QuizStep.LIMITATIONS -> true
            QuizStep.HEALTH -> !health.hasAnyRisk || health.acknowledgedRisk   // gate: sem risco OU reconhecido
        }
}