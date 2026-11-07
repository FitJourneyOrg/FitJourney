package dev.rafael.contract.profile

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * "Tempo de treino consistente" (fatia 12 — questionário e triagem). Pergunta nova do
 * onboarding; junto com [TimeOffTraining] substitui a escolha livre de [Level] — ver
 * `docs/regras/16-onboarding-e-triagem.md`.
 */
@Serializable
enum class TrainingConsistency {
    @SerialName("NEVER_TRAINED") NEVER_TRAINED,
    @SerialName("LESS_THAN_6_MONTHS") LESS_THAN_6_MONTHS,
    @SerialName("SIX_MONTHS_TO_2_YEARS") SIX_MONTHS_TO_2_YEARS,
    @SerialName("MORE_THAN_2_YEARS") MORE_THAN_2_YEARS,
}
