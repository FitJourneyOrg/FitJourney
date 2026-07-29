package dev.rafael.features.profile.presentation.state

import dev.rafael.contract.profile.BodyLimitation
import dev.rafael.contract.profile.Goal
import dev.rafael.contract.profile.HealthScreening
import dev.rafael.contract.profile.Level
import dev.rafael.contract.profile.MuscleGroup
import dev.rafael.contract.profile.SplitType
import dev.rafael.contract.profile.TrainingEnvironment

/** Os passos do quiz, em ordem. SPLIT (ARCH #29) entra após ENVIRONMENT. */
enum class QuizStep { GOAL, LEVEL, DAYS, FOCUS, BODY, ENVIRONMENT, SPLIT, HEALTH, LIMITATIONS }
data class QuizState(
    val step: QuizStep = QuizStep.GOAL,
    val goal: Goal? = null,
    val level: Level? = null,
    val daysPerWeek: Int? = null,
    val splitPreference: SplitType? = null,   // ARCH #29: null = usa o recomendado
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


    /** Foco só faz sentido p/ INTER/ADVANCED e não p/ saúde geral — espelha o motor (#26/#24). */
    val focusEligible: Boolean
        get() = level != Level.BEGINNER && goal != Goal.GENERAL_HEALTH

    /** Passos visíveis: iniciante (ou saúde geral) não veem o passo de FOCO. */
    val visibleSteps: List<QuizStep>
        get() = QuizStep.entries.filter { it != QuizStep.FOCUS || focusEligible }

    val canAdvance: Boolean
        get() = when (step) {
            QuizStep.GOAL -> goal != null
            QuizStep.LEVEL -> level != null
            QuizStep.DAYS -> daysPerWeek != null
            QuizStep.FOCUS -> true
            QuizStep.BODY -> true
            QuizStep.ENVIRONMENT -> environment != null      // obrigatório escolher
            QuizStep.SPLIT -> true                            // null = recomendado (pré-selecionado)
            QuizStep.LIMITATIONS -> true
            QuizStep.HEALTH -> !health.hasAnyRisk || health.acknowledgedRisk   // gate: sem risco OU reconhecido
        }
}