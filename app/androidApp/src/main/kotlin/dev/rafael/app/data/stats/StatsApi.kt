package dev.rafael.app.data.stats

import dev.rafael.contract.stats.UserStatsDto
import dev.rafael.core.network.HttpClientFactory
import dev.rafael.core.network.httpResult
import dev.rafael.core.result.AppResult
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get

/**
 * XP/nível/streak do usuário (ARCH #16). Só leitura: o cliente NUNCA envia XP — o servidor
 * deriva tudo das sessões. Mesmo atalho do SessionApi: vive no módulo app por ora
 * (débito conhecido: extrair para uma feature própria junto com session:data).
 */
class StatsApi(private val client: HttpClient) {
    suspend fun get(): AppResult<UserStatsDto> =
        httpResult { client.get("${HttpClientFactory.BASE_URL}/me/stats").body() }
}
