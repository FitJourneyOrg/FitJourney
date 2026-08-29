package dev.rafael.contract.notificacao

import kotlinx.serialization.Serializable

/**
 * Corpo do `POST /me/devices` (F.1).
 *
 * **Só o token.** O `userId` não vem daqui: quem registra é quem está autenticado. Aceitá-lo do
 * cliente deixaria qualquer um redirecionar as notificações de outra pessoa para o próprio
 * aparelho.
 */
@Serializable
data class RegistrarDispositivoRequest(val token: String)
