package dev.rafael.features.profile.presentation.state

import dev.rafael.contract.profile.Goal
import dev.rafael.contract.profile.Level
import dev.rafael.contract.profile.MuscleGroup

/** Os passos do quiz, em ordem. (Equipamento sai pra Fase 4.) */
enum class QuizStep { GOAL, LEVEL, DAYS, FOCUS, BODY }

data class QuizState(
    val step: QuizStep = QuizStep.GOAL,
    // respostas (obrigatórias começam null = não respondido)
    val goal: Goal? = null,
    val level: Level? = null,
    val daysPerWeek: Int? = null,
    val focusAreas: List<MuscleGroup> = emptyList(),
    val weightKg: Double? = null,
    val heightCm: Double? = null,
    // ciclo
    val isSubmitting: Boolean = false,
    val error: String? = null,
    val completed: Boolean = false,
) {
    /** O passo atual está respondido o suficiente pra avançar? (validação leve de UX) */
    val canAdvance: Boolean
        get() = when (step) {
            QuizStep.GOAL -> goal != null
            QuizStep.LEVEL -> level != null
            QuizStep.DAYS -> daysPerWeek != null
            QuizStep.FOCUS -> true            // 0-2 é válido; vazio = equilibrado
            QuizStep.BODY -> true             // peso/altura opcionais (pode pular)
        }
}