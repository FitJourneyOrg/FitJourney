package dev.rafael.features.stats.data

import dev.rafael.contract.stats.UserStatsDto
import dev.rafael.core.network.HttpClientFactory
import dev.rafael.core.network.httpResult
import dev.rafael.core.result.AppResult
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get

/**
 * XP/nível/streak do usuário (ARCH #16). Só leitura: o cliente NUNCA envia XP — o servidor
 * deriva tudo das sessões.
 */
class StatsApi(private val client: HttpClient) {
    suspend fun get(): AppResult<UserStatsDto> =
        httpResult { client.get("${HttpClientFactory.BASE_URL}/me/stats").body() }
}
