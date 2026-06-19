package dev.rafael.server.plugins

import dev.rafael.core.result.AppResult
import dev.rafael.server.auth.FirebaseUser
import dev.rafael.server.auth.TokenVerifier
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.bearer

const val FIREBASE_AUTH = "firebase"

fun Application.configureAuthentication() {
    install(Authentication) {
        bearer(FIREBASE_AUTH) {
            authenticate { credential ->
                when (val result = TokenVerifier.verify(credential.token)) {
                    is AppResult.Success -> result.value
                    is AppResult.Failure -> null
                }
            }
        }
    }
}