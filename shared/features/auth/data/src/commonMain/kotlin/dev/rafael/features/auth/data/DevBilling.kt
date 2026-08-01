package dev.rafael.features.auth.data

import dev.rafael.core.network.httpResult
import dev.rafael.core.result.AppResult
import dev.rafael.features.auth.domain.repository.Billing

/**
 * Compra simulada (Fase 7 dev): chama /me/subscribe e o servidor liga o premium.
 * Trocar por `PlayBilling` quando houver Play Console — a UI que chama `subscribe()` não muda.
 */
class DevBilling(private val meDataSource: MeDataSource) : Billing {
    override suspend fun subscribe(): AppResult<Unit> =
        httpResult<Unit> { meDataSource.subscribe() }   // descarta o UserDto; só interessa o sucesso
}
