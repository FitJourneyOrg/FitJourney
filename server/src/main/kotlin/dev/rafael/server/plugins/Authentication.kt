package dev.rafael.server.plugins

import dev.rafael.core.result.AppResult
import dev.rafael.server.auth.TokenVerifier
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.bearer
import org.koin.ktor.ext.get
import io.ktor.server.auth.Authentication as KtorAuthentication

const val FIREBASE_AUTH = "firebase"

fun Application.configureAuthentication() {
    val verifier = get<TokenVerifier>()
    install(KtorAuthentication) {
        bearer(FIREBASE_AUTH) {
            authenticate { credential ->
                when (val result = verifier.verify(credential.token)) {
                    is AppResult.Success -> result.value
                    is AppResult.Failure -> null
                }
            }
        }
    }
}