package dev.rafael.app.data.session

import dev.rafael.contract.session.WorkoutSessionDto
import dev.rafael.core.network.HttpClientFactory
import dev.rafael.core.network.httpResult
import dev.rafael.core.result.AppResult
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType

class SessionApi(private val client: HttpClient) {
    private val base = "${HttpClientFactory.BASE_URL}/sessions"

    suspend fun post(dto: WorkoutSessionDto): AppResult<Unit> =
        httpResult<Unit> {
            client.post(base) {
                contentType(ContentType.Application.Json)
                setBody(dto)
            }
        }

    suspend fun list(): AppResult<List<WorkoutSessionDto>> =
        httpResult { client.get(base).body() }
}
