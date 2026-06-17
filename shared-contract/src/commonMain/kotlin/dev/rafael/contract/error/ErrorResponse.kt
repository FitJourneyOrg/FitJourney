package dev.rafael.contract.error

import kotlinx.serialization.Serializable

/**
 * Envelope de erro único da API — cliente E servidor leem este DTO.
 * `code`: string estável p/ o cliente ramificar sem parsear `message`.
 *         String (não enum) no fio: código novo no server não quebra cliente antigo.
 * `fieldErrors`: validação campo→mensagem (forms de auth/onboarding nas Fases 2/3).
 */
@Serializable
data class ErrorResponse(
    val code: String,
    val message: String,
    val fieldErrors: Map<String, String> = emptyMap(),
)