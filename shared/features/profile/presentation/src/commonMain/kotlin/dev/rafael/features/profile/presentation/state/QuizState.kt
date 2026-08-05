package dev.rafael.features.profile.presentation.state

import dev.rafael.contract.profile.BodyLimitation
import dev.rafael.contract.profile.Goal
import dev.rafael.contract.profile.HealthScreening
import dev.rafael.contract.profile.Level
import dev.rafael.contract.profile.MuscleGroup
import dev.rafael.contract.profile.SplitType
import dev.rafael.contract.profile.TrainingEnvironment

/** Os passos do quiz, em ordem. REST_DAYS (Estágio 2) após DAYS; SPLIT (ARCH #29) após ENVIRONMENT. */
enum class QuizStep { GOAL, LEVEL, AGE, DAYS, REST_DAYS, FOCUS, BODY, ENVIRONMENT, SPLIT, HEALTH, LIMITATIONS }
data class QuizState(
    val step: QuizStep = QuizStep.GOAL,
    val goal: Goal? = null,
    val level: Level? = null,
    val daysPerWeek: Int? = null,
    val splitPreference: SplitType? = null,   // ARCH #29: null = usa o recomendado
    val unavailableDays: List<Int> = emptyList(),   // dias (1=Seg..7=Dom) que NÃO quer treinar
    val focusAreas: List<MuscleGroup> = emptyList(),
    val weightKg: Double? = null,
    val heightCm: Double? = null,
    val age: Int? = null,                  // #24
    val minorSupervised: Boolean = false,  // #24
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

    /** Sobra dia suficiente pra treinar o que o usuário pediu? (7 - dias off >= daysPerWeek) */
    val hasEnoughFreeDays: Boolean
        get() = 7 - unavailableDays.size >= (daysPerWeek ?: 0)

    val canAdvance: Boolean
        get() = when (step) {
            QuizStep.GOAL -> goal != null
            QuizStep.LEVEL -> level != null
            // #24: idade obrigatória e válida; menor de 18 só passa com o aceite de supervisão.
            QuizStep.AGE -> age != null && age in 5..120 && (age >= 18 || minorSupervised)
            QuizStep.DAYS -> daysPerWeek != null
            QuizStep.REST_DAYS -> hasEnoughFreeDays        // não pode marcar dias off demais
            QuizStep.FOCUS -> true
            QuizStep.BODY -> true
            QuizStep.ENVIRONMENT -> environment != null      // obrigatório escolher
            QuizStep.SPLIT -> true                            // null = recomendado (pré-selecionado)
            QuizStep.LIMITATIONS -> true
            QuizStep.HEALTH -> !health.hasAnyRisk || health.acknowledgedRisk   // gate: sem risco OU reconhecido
        }
}