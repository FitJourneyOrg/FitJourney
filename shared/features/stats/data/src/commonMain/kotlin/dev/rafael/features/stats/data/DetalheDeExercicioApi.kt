package dev.rafael.features.stats.data

import dev.rafael.contract.stats.ExercicioDetalheDto
import dev.rafael.core.network.HttpClientFactory
import dev.rafael.core.network.httpResult
import dev.rafael.core.result.AppResult
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter

/**
 * Detalhe de UM exercicio dentro de UM programa (J.5). So leitura, ONLINE-ONLY — sem cache,
 * ver KDoc do [dev.rafael.features.stats.domain.DetalheDeExercicio].
 */
class DetalheDeExercicioApi(private val client: HttpClient) {
    suspend fun get(
        locale: String,
        programId: String,
        exercicioId: String,
    ): AppResult<ExercicioDetalheDto> =
        httpResult {
            client.get("${HttpClientFactory.BASE_URL}/me/progress/exercicio") {
                parameter("locale", locale)
                parameter("programId", programId)
                parameter("exercicioId", exercicioId)
            }.body()
        }
}
