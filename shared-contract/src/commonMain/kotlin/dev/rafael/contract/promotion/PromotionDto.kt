package dev.rafael.contract.promotion

import dev.rafael.contract.profile.Level
import kotlinx.serialization.Serializable

/**
 * `GET /me/promotion` — sempre 200. A sugestão é DERIVADA, nunca persistida (mesma decisão do
 * `PromotionDeclineRepository`, Fatia 11): o servidor recalcula a cada chamada a partir do
 * histórico de progressão, então o campo é nullable em vez de o endpoint variar entre 200 e 404.
 *
 * Sem `POST /me/promotion/accept` de propósito. O aceite não é uma transição de estado no
 * servidor — é o cliente chamar o `PUT /me/profile` que já existe, com `level` novo, o MESMO
 * caminho do onboarding. Na próxima leitura o diagnóstico não dispara mais (o perfil já não é
 * `BEGINNER`) e a sugestão desaparece por si. `level` é autodeclarado por desenho (Fatia 11: o
 * nível barra exigência técnica, o motor nunca promove sozinho) — um aceite server-authoritative
 * fecharia essa porta lateral deixando a da frente aberta.
 */
@Serializable
data class PromotionStatusDto(
    val suggestion: PromotionSuggestionDto? = null,
)

/** `nivelProposto` é sempre o PRÓXIMO nível, nunca o atual — hoje só `INTERMEDIATE` (iniciante). */
@Serializable
data class PromotionSuggestionDto(
    val nivelProposto: Level,
)
