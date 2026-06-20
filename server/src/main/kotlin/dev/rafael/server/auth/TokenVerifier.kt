package dev.rafael.server.auth

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import dev.rafael.core.result.AppError
import dev.rafael.core.result.AppResult
import dev.rafael.core.result.asFailure
import dev.rafael.core.result.asSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Token rejeitado (inválido/expirado). Exceção do domínio do server — não vaza tipo do Google. */
class InvalidTokenException(message: String, cause: Throwable? = null) : Exception(message, cause)

fun interface TokenDecoder {
    fun decode(idToken: String): FirebaseUser
}

/** Impl real: delega ao Firebase e traduz a exceção do Google p/ a nossa. */
class FirebaseTokenDecoder(private val auth: FirebaseAuth) : TokenDecoder {
    override fun decode(idToken: String): FirebaseUser {
        val decoded = try {
            auth.verifyIdToken(idToken)
        } catch (e: FirebaseAuthException) {
            throw InvalidTokenException("Token inválido ou expirado", e)
        }
        return FirebaseUser(
            uid = decoded.uid,
            email = decoded.email,
            emailVerified = decoded.isEmailVerified,
        )
    }
}

class TokenVerifier(private val decoder: TokenDecoder) {
    suspend fun verify(idToken: String): AppResult<FirebaseUser> = withContext(Dispatchers.IO) {
        runCatching { decoder.decode(idToken) }.fold(
            onSuccess = { it.asSuccess() },
            onFailure = { e ->
                when (e) {
                    is InvalidTokenException -> AppError.Unauthorized(e.message ?: "Token inválido").asFailure()
                    else -> AppError.Unexpected("Falha ao validar token", e).asFailure()
                }
            },
        )
    }
}