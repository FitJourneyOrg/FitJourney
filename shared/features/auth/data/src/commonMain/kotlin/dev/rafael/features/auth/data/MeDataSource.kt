package dev.rafael.features.auth.data

import dev.rafael.contract.user.UserDto
import dev.rafael.core.network.HttpClientFactory
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post

class MeDataSource(private val client: HttpClient) {
    suspend fun getMe(): UserDto =
        client.get("${HttpClientFactory.BASE_URL}/me").body()

    /** POST /me/subscribe — ativa o premium (compra simulada na Fase 7 dev). */
    suspend fun subscribe(): UserDto =
        client.post("${HttpClientFactory.BASE_URL}/me/subscribe").body()
}