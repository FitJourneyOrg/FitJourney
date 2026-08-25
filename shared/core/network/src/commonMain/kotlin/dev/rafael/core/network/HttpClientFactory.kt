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

    /**
     * Onde o servidor está. **Configurável no build**, não fixo no código.
     *
     * `10.0.2.2` é um apelido de NAT que só existe **dentro do emulador** — no aparelho físico ele
     * não resolve, e a falha chega ao app como "o servidor não respondeu", que é verdade e não
     * ajuda em nada. Como o endereço muda conforme onde o app roda (emulador, aparelho na mesma
     * rede, produção), ele não pode ser uma constante compilada.
     *
     * Quem define é o `androidApp`, no `onCreate`, a partir do `BuildConfig` — que por sua vez sai
     * de uma propriedade Gradle. Assim trocar de aparelho é uma linha no `gradle.properties`, e
     * não uma edição em código compartilhado que entraria num commit por engano.
     *
     * `var` incomoda, e com razão: é estado global mutável. O que segura isso é ser escrito **uma
     * vez só, na inicialização**, antes de qualquer requisição. Ver DEBITOS: o certo é injetar a
     * base pelo Koin, e isso mexe em todas as classes `*Api`.
     */
    var BASE_URL: String = "http://10.0.2.2:8080"

    fun create(
        engine: HttpClientEngine,
        tokenProvider: TokenProvider,
        sessionExpiry: SessionExpiryBus,
    ): HttpClient =
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
                    /**
                     * SEM ESTE BLOCO o Ktor guarda o token do `loadTokens` e nunca mais o
                     * recarrega. Token do Firebase dura ~1h: passada essa hora, TODO request
                     * virava 401 permanente e o app dizia "sessão expirada" sem chance de
                     * recuperação — mesmo com o usuário perfeitamente logado.
                     *
                     * Aqui o `currentToken()` volta ao Firebase, que renova sozinho quando o
                     * token venceu. Se voltar nulo ou idêntico ao que já falhou, aí a sessão
                     * morreu de verdade (refresh token revogado, conta removida): sinaliza,
                     * e o app manda pro login em vez de oferecer um retry que nunca funciona.
                     */
                    refreshTokens {
                        val novo = tokenProvider.currentToken()
                        if (novo == null || novo == oldTokens?.accessToken) {
                            sessionExpiry.sinalizar()
                            null
                        } else {
                            BearerTokens(novo, "")
                        }
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