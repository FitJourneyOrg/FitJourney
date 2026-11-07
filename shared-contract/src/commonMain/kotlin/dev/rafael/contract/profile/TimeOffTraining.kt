package dev.rafael.contract.profile

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * "Há quanto tempo parou" (fatia 12 — questionário e triagem). Pergunta nova do onboarding;
 * junto com [TrainingConsistency] substitui a escolha livre de [Level] — ver
 * `docs/regras/16-onboarding-e-triagem.md`.
 */
@Serializable
enum class TimeOffTraining {
    @SerialName("NONE") NONE,
    @SerialName("UP_TO_1_MONTH") UP_TO_1_MONTH,
    @SerialName("ONE_TO_6_MONTHS") ONE_TO_6_MONTHS,
    @SerialName("MORE_THAN_6_MONTHS") MORE_THAN_6_MONTHS,
}
