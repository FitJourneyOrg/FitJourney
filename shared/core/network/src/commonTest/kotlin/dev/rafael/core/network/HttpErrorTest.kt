package dev.rafael.core.network

import dev.rafael.core.result.AppError
import dev.rafael.core.result.AppResult
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Prova o mapeador único de erro HTTP (httpResult/mapHttpError): cada status vira o AppError
 * certo, com message/code do ErrorResponse do servidor. Usa o MockEngine do Ktor (sem rede).
 */
class HttpErrorTest {

    private fun client(status: HttpStatusCode, body: String): HttpClient =
        HttpClient(MockEngine {
            respond(
                content = body,
                status = status,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }) {
            expectSuccess = true   // 4xx/5xx lançam ResponseException → httpResult captura
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }

    private fun errorBody(code: String, message: String) =
        """{"code":"$code","message":"$message"}"""

    private suspend fun call(client: HttpClient): AppResult<String> =
        httpResult { client.get("https://x/").body<String>() }

    @Test
    fun `401 vira Unauthorized`() = runTest {
        val r = call(client(HttpStatusCode.Unauthorized, errorBody("UNAUTHORIZED", "expirou")))
        assertTrue(r is AppResult.Failure && r.error is AppError.Unauthorized)
    }

    @Test
    fun `403 vira Forbidden com o code do servidor`() = runTest {
        val r = call(client(HttpStatusCode.Forbidden, errorBody("ENTITLEMENT_REQUIRED", "premium")))
        assertIs<AppResult.Failure>(r)
        val err = r.error
        assertIs<AppError.Forbidden>(err)
        assertEquals("ENTITLEMENT_REQUIRED", err.code, "code do servidor tem que sobreviver (a paywall depende dele)")
    }

    @Test
    fun `400 vira Validation`() = runTest {
        val r = call(client(HttpStatusCode.BadRequest, errorBody("VALIDATION", "dado inválido")))
        assertTrue(r is AppResult.Failure && r.error is AppError.Validation)
    }

    @Test
    fun `404 vira NotFound`() = runTest {
        val r = call(client(HttpStatusCode.NotFound, errorBody("NOT_FOUND", "sumiu")))
        assertTrue(r is AppResult.Failure && r.error is AppError.NotFound)
    }

    @Test
    fun `500 vira Unexpected`() = runTest {
        val r = call(client(HttpStatusCode.InternalServerError, "erro"))
        assertTrue(r is AppResult.Failure && r.error is AppError.Unexpected)
    }

    @Test
    fun `sucesso vira Success`() = runTest {
        val r = call(client(HttpStatusCode.OK, "ok"))
        assertIs<AppResult.Success<String>>(r)
    }
}
