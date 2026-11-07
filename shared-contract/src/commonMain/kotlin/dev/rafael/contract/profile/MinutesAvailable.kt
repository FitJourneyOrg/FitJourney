package dev.rafael.contract.profile

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Minutos disponíveis para a sessão de treino (onboarding, fatia 12 -- resto). Pergunta em
 * faixas (especificação, "Os minutos voltaram"), não minutagem livre: a pessoa não sabe quanto o
 * treino leva, mas sabe quanto tempo tem. `minutos` é o valor representativo que alimenta
 * `CapacityModel.tetoPorTempo` (motor v3, dormente -- nenhuma rota ainda chama o pipeline).
 */
@Serializable
enum class MinutesAvailable(val minutos: Int) {
    @SerialName("FORTY_FIVE") FORTY_FIVE(45),
    @SerialName("SIXTY") SIXTY(60),
    @SerialName("SEVENTY_FIVE") SEVENTY_FIVE(75),
    @SerialName("NINETY_PLUS") NINETY_PLUS(90),
}
