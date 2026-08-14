package dev.rafael.core.network

import dev.rafael.contract.error.ErrorResponse
import dev.rafael.core.result.AppError
import dev.rafael.core.result.AppResult
import dev.rafael.core.result.asFailure
import dev.rafael.core.result.asSuccess
import io.ktor.client.call.body
import io.ktor.client.plugins.ResponseException
import io.ktor.http.HttpStatusCode

/**
 * Roda um request e mapeia falhas HTTP p/ AppError de forma CONSISTENTE em todas as features.
 * Antes cada data-source achatava diferente (profile/auth/exercise caíam em Unexpected); aqui
 * 401/403/400/404/409 viram o tipo certo, com a mensagem/código do ErrorResponse do servidor.
 */
suspend fun <T> httpResult(block: suspend () -> T): AppResult<T> =
    runCatching { block() }.fold(
        onSuccess = { it.asSuccess() },
        onFailure = { mapHttpError(it).asFailure() },
    )

/** Traduz a exceção de um request em AppError. Lê o ErrorResponse do corpo quando existe. */
suspend fun mapHttpError(e: Throwable): AppError {
    // Não é resposta HTTP: nem chegamos a falar com o servidor (sem rede, recusa de conexão,
    // timeout, DNS). Vira Connection — e NÃO Unexpected, senão o app não consegue distinguir
    // "estou offline" de "o servidor devolveu 500", que pedem UI e fallback diferentes.
    if (e !is ResponseException) return AppError.Connection(cause = e)
    val body = runCatching { e.response.body<ErrorResponse>() }.getOrNull()
    val msg = body?.message
    return when (e.response.status) {
        HttpStatusCode.Unauthorized -> AppError.Unauthorized(msg ?: "Sessão expirada. Faça login novamente.")
        HttpStatusCode.Forbidden -> AppError.Forbidden(msg ?: "Sem permissão", body?.code)
        HttpStatusCode.BadRequest -> AppError.Validation(msg ?: "Dados inválidos", body?.fieldErrors ?: emptyMap())
        HttpStatusCode.NotFound -> AppError.NotFound(msg ?: "Não encontrado")
        HttpStatusCode.Conflict -> AppError.Conflict(msg ?: "Conflito de estado")
        // O servidor existe mas não está atendendo (proxy no ar, app derrubado, deploy em curso).
        // Para o cliente é indistinguível de queda de rede: mesma UI, mesmo fallback de cache.
        HttpStatusCode.BadGateway,
        HttpStatusCode.ServiceUnavailable,
        HttpStatusCode.GatewayTimeout -> AppError.Connection(cause = e)
        else -> AppError.Unexpected(msg ?: "Falha na operação", e)
    }
}
