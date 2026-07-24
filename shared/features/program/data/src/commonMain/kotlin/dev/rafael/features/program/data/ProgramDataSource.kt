package dev.rafael.features.program.data

import dev.rafael.contract.program.CreateManualProgramRequest
import dev.rafael.contract.program.ProgramDto
import dev.rafael.contract.program.RenameProgramRequest
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

class ProgramDataSource(private val client: HttpClient) {
    private val base = "${HttpClientFactory.BASE_URL}/programs"

    suspend fun list(): List<ProgramDto> =
        client.get(base).body()

    /** POST /programs/generate — sem corpo; o servidor lê o perfil pelo token. */
    suspend fun generate(): ProgramDto =
        client.post("$base/generate").body()

    suspend fun createManual(name: String): ProgramDto =
        client.post(base) {
            contentType(ContentType.Application.Json)
            setBody(CreateManualProgramRequest(name))
        }.body()

    suspend fun rename(id: String, name: String): ProgramDto =
        client.put("$base/$id") {
            contentType(ContentType.Application.Json)
            setBody(RenameProgramRequest(name))
        }.body()

    suspend fun delete(id: String) {
        client.delete("$base/$id")   // 204/Unit; expectSuccess lança em 4xx
    }
}
