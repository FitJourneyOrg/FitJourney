package dev.rafael.features.stats.data

import dev.rafael.contract.stats.ProgressDto
import dev.rafael.core.network.HttpClientFactory
import dev.rafael.core.network.httpResult
import dev.rafael.core.result.AppResult
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter

/**
 * Analise de progressao (J.2). So leitura — o cliente nunca calcula nada disto (ARCH #16).
 *
 * O `locale` vai na QUERY, e nao em cabecalho, pela [REGRA] da G.1: o nome do exercicio volta
 * traduzido, e quem le para MOSTRAR declara em que idioma.
 */
class ProgressApi(private val client: HttpClient) {
    suspend fun get(locale: String): AppResult<ProgressDto> =
        httpResult {
            client.get("${HttpClientFactory.BASE_URL}/me/progress") {
                parameter("locale", locale)
            }.body()
        }
}
