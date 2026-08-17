package dev.rafael.app.data.achievements

import dev.rafael.contract.stats.AchievementDto
import dev.rafael.core.network.HttpClientFactory
import dev.rafael.core.network.httpResult
import dev.rafael.core.result.AppResult
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get

/**
 * Conquistas (ARCH #16). Só leitura — o cliente nunca concede.
 *
 * Este GET tem um efeito colateral no servidor: ele avalia o progresso e concede o que faltar.
 * É de propósito (dá retroativo de graça) e idempotente, mas explica por que a resposta pode
 * trazer medalha nova sem nenhuma escrita partindo daqui.
 *
 * Mesmo atalho do StatsApi: vive no módulo app por ora (débito conhecido — extrair junto com
 * stats e session para features próprias).
 */
class AchievementsApi(private val client: HttpClient) {
    suspend fun get(): AppResult<List<AchievementDto>> =
        httpResult { client.get("${HttpClientFactory.BASE_URL}/me/achievements").body() }
}
