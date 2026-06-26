package dev.rafael.features.auth.data

import dev.rafael.contract.user.UserDto
import dev.rafael.core.network.HttpClientFactory
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get

class MeDataSource(private val client: HttpClient) {
    suspend fun getMe(): UserDto =
        client.get("${HttpClientFactory.BASE_URL}/me").body()
}