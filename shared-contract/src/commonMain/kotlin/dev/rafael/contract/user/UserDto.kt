package dev.rafael.contract.user

import kotlinx.serialization.Serializable

/**
 * Usuário exposto pela API (resposta do /me).
 * Omite firebase_uid de propósito: é detalhe interno da auth, o cliente já tem o uid.
 * `id` é o identificador interno (UUID) — é o que features futuras vão referenciar.
 */
@Serializable
data class UserDto(
    val id: String,
    val email: String?,
    val isPremium: Boolean = false,   // <- novo, default false (não quebra clientes antigos)
)