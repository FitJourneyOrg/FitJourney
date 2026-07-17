package dev.rafael.contract.profile

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Limitação física declarada pelo usuário no onboarding. Filtra exercícios
 * contraindicados na geração (§3.2, ARCH #20 — exclusão dura em SQL).
 *
 * Espelha 1:1 as contraindications da taxonomia do exercício. NÃO confundir
 * com HealthScreening (PAR-Q): aquele é o gate "pode treinar?"; este é o
 * filtro "quais exercícios evitar?".
 */
@Serializable
enum class BodyLimitation {
    @SerialName("SHOULDER") SHOULDER,
    @SerialName("KNEE") KNEE,
    @SerialName("LUMBAR") LUMBAR,
    @SerialName("WRIST") WRIST,
    @SerialName("IMPACT") IMPACT,
}