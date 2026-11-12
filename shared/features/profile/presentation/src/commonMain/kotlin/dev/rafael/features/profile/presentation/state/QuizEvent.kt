package dev.rafael.features.profile.presentation.state

import dev.rafael.contract.profile.Accessory
import dev.rafael.contract.profile.BodyLimitation
import dev.rafael.contract.profile.Goal
import dev.rafael.contract.profile.MinutesAvailable
import dev.rafael.contract.profile.MuscleGroup
import dev.rafael.contract.profile.SplitType
import dev.rafael.contract.profile.TimeOffTraining
import dev.rafael.contract.profile.TrainingConsistency
import dev.rafael.contract.profile.TrainingEnvironment

sealed interface QuizEvent {
    data class GoalSelected(val goal: Goal) : QuizEvent
    data class TrainingConsistencySelected(val value: TrainingConsistency) : QuizEvent   // fatia 12
    data class TimeOffTrainingSelected(val value: TimeOffTraining) : QuizEvent             // fatia 12
    data class LightModeSelected(val value: Boolean) : QuizEvent   // fatia 12 (resto): "quer comecar no modo leve?"
    data class DaysSelected(val days: Int) : QuizEvent
    data class MinutesAvailableSelected(val value: MinutesAvailable) : QuizEvent   // fatia 12 (resto)
    data class SplitSelected(val split: SplitType) : QuizEvent   // ARCH #29
    data class RestDayToggled(val dayOfWeek: Int) : QuizEvent     // Estágio 2: liga/desliga dia off
    data class FocusToggled(val muscle: MuscleGroup) : QuizEvent
    data class WeightChanged(val value: Double?) : QuizEvent
    data class HeightChanged(val value: Double?) : QuizEvent
    data class AgeChanged(val value: Int?) : QuizEvent            // #24
    data object SupervisedToggled : QuizEvent                     // #24: <18 aceite de supervisão
    data class EnvironmentSelected(val env: TrainingEnvironment) : QuizEvent
    /** Fatia 12 (resto): botao "nao tenho este equipamento" tambem na ACADEMIA (academias de
     * condominio). Revela o passo ACCESSORIES pra quem marcar. */
    data object AcademyMissingEquipmentToggled : QuizEvent
    data class HealthToggled(val field: HealthField) : QuizEvent   // qual pergunta mudou
    data object AcknowledgedRiskToggled : QuizEvent
    data class LimitationToggled(val limitation: BodyLimitation) : QuizEvent
    data class AccessoryToggled(val accessory: Accessory) : QuizEvent

    enum class HealthField { CARDIAC, CHEST_PAIN, JOINT, MEDICATION }
    /** Modo edição (pós-onboarding): busca o perfil salvo e pré-popula o estado inteiro. */
    data object StartEditing : QuizEvent
    data object Next : QuizEvent       // avança o passo (ou submete, se for o último)
    data object Back : QuizEvent       // volta o passo
}