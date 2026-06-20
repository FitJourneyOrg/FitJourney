package dev.rafael.server.auth

import dev.rafael.core.result.AppError
import dev.rafael.core.result.AppResult
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertEquals

class TokenVerifierTest {

    @Test
    fun `token invalido vira Unauthorized`(): Unit = runBlocking {
        val decoder = TokenDecoder { throw InvalidTokenException("Token inválido ou expirado") }
        val result = TokenVerifier(decoder).verify("token-podre")

        assertIs<AppResult.Failure>(result)
        assertIs<AppError.Unauthorized>(result.error)

    }

    @Test
    fun `erro inesperado vira Unexpected`(): Unit = runBlocking {
        val decoder = TokenDecoder { throw RuntimeException("falha de rede") }
        val result = TokenVerifier(decoder).verify("qualquer")

        assertIs<AppResult.Failure>(result)
        assertIs<AppError.Unexpected>(result.error)
    }

    @Test
    fun `token valido vira Success com o usuario`() = runBlocking {
        val expected = FirebaseUser("uid-123", "a@b.com", emailVerified = true)
        val decoder = TokenDecoder { expected }
        val result = TokenVerifier(decoder).verify("token-bom")

        assertIs<AppResult.Success<FirebaseUser>>(result)
        assertEquals("uid-123", result.value.uid)
    }
}