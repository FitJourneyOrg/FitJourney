package dev.rafael.features.exercise.data.repository.remote

import dev.rafael.contract.exercise.ExerciseCategory
import dev.rafael.contract.exercise.ExerciseDto
import dev.rafael.core.network.HttpClientFactory
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter

class ExerciseRemoteDataSource(private val client: HttpClient) {
    suspend fun getExercises(category: ExerciseCategory?): List<ExerciseDto> =
        client.get("${HttpClientFactory.BASE_URL}/exercises") {
            category?.let { parameter("category", it.name) }
        }.body()
}