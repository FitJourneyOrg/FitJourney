package dev.rafael.features.profile.data

import dev.rafael.contract.profile.ProfileDto
import dev.rafael.core.network.HttpClientFactory
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType

class ProfileDataSource(private val client: HttpClient) {
    suspend fun getProfile(): ProfileDto =
        client.get("${HttpClientFactory.BASE_URL}/me/profile").body()

    suspend fun saveProfile(dto: ProfileDto): ProfileDto =
        client.put("${HttpClientFactory.BASE_URL}/me/profile") {
            contentType(ContentType.Application.Json)
            setBody(dto)
        }.body()
}