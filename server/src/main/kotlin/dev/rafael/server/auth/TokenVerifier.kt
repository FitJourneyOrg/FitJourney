package dev.rafael.server.auth

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import dev.rafael.core.result.AppError
import dev.rafael.core.result.AppResult
import dev.rafael.core.result.asFailure
import dev.rafael.core.result.asSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object TokenVerifier {
    /** Valida o ID Token (Bearer) e extrai a identidade. Bloqueante -> IO. */
    suspend fun verify(idToken: String): AppResult<FirebaseUser> = withContext(Dispatchers.IO) {
        runCatching {
            val decoded = FirebaseAuth.getInstance().verifyIdToken(idToken)
            FirebaseUser(
                uid = decoded.uid,
                email = decoded.email,
                emailVerified = decoded.isEmailVerified,
            )
        }.fold(
            onSuccess = { it.asSuccess() },
            onFailure = { e ->
                when (e) {
                    is FirebaseAuthException ->
                        AppError.Unauthorized("Token inválido ou expirado").asFailure()
                    else ->
                        AppError.Unexpected("Falha ao validar token", e).asFailure()
                }
            },
        )
    }
}