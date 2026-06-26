package dev.rafael.features.profile.presentation.state

import dev.rafael.contract.profile.Goal
import dev.rafael.contract.profile.Level
import dev.rafael.contract.profile.MuscleGroup

sealed interface QuizEvent {
    data class GoalSelected(val goal: Goal) : QuizEvent
    data class LevelSelected(val level: Level) : QuizEvent
    data class DaysSelected(val days: Int) : QuizEvent
    data class FocusToggled(val muscle: MuscleGroup) : QuizEvent
    data class WeightChanged(val value: Double?) : QuizEvent
    data class HeightChanged(val value: Double?) : QuizEvent
    data object Next : QuizEvent       // avança o passo (ou submete, se for o último)
    data object Back : QuizEvent       // volta o passo
}