package dev.rafael.core.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.authProviders
import io.ktor.client.plugins.auth.providers.BearerAuthProvider
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

object HttpClientFactory {
    const val BASE_URL = "http://10.0.2.2:8080"

    fun create(engine: HttpClientEngine, tokenProvider: TokenProvider): HttpClient =
        HttpClient(engine) {
            expectSuccess = true
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                })
            }
            install(Auth) {
                bearer {
                    loadTokens {
                        tokenProvider.currentToken()?.let { BearerTokens(it, "") }
                    }
                }
            }
            defaultRequest {
                contentType(ContentType.Application.Json)
            }
        }
}

/**
 * Invalida o bearer token cacheado pelo Ktor. O provider `bearer` só recarrega o token no
 * 401 — mas um token do Firebase segue válido ~1h após o signOut (signOut é client-side).
 * Sem isso, ao trocar de usuário (logout + novo login), o cliente continua mandando o token
 * ANTIGO (ainda válido) e o servidor devolve os dados do usuário anterior. Chamar em toda
 * troca de sessão (signIn/signUp/signOut) força o próximo request a recarregar o token atual.
 */
fun HttpClient.clearBearerToken() {
    authProviders
        .filterIsInstance<BearerAuthProvider>()
        .firstOrNull()
        ?.clearToken()
}