package dev.rafael.features.auth.domain.repository

import dev.rafael.core.result.AppResult

/**
 * Passo de COMPRA da assinatura, atrás de interface (Fase 7). A UI só chama `subscribe()`.
 * - Hoje: `DevBilling` liga o premium direto via POST /me/subscribe (compra simulada).
 * - Depois: `PlayBilling` roda o Google Play Billing e, no sucesso, ativa o premium — sem
 *   tocar na UI nem no entitlement/blur (que já respeitam is_premium).
 */
fun interface Billing {
    suspend fun subscribe(): AppResult<Unit>
}
