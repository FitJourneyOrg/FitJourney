package dev.rafael.features.workout.data

import dev.rafael.contract.workout.WorkoutDto
import dev.rafael.contract.workout.WorkoutSummaryDto
import dev.rafael.core.network.HttpClientFactory
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType

class WorkoutDataSource(private val client: HttpClient) {
    private val base = "${HttpClientFactory.BASE_URL}/workouts"

    suspend fun list(): List<WorkoutSummaryDto> =
        client.get(base).body()

    suspend fun get(id: String): WorkoutDto =
        client.get("$base/$id").body()

    suspend fun create(dto: WorkoutDto): WorkoutDto =
        client.post(base) { contentType(ContentType.Application.Json); setBody(dto) }.body()

    suspend fun update(id: String, dto: WorkoutDto): WorkoutDto =
        client.put("$base/$id") { contentType(ContentType.Application.Json); setBody(dto) }.body()

    suspend fun delete(id: String) {
        client.delete("$base/$id")   // 204/Unit; expectSuccess lança em 4xx
    }
}