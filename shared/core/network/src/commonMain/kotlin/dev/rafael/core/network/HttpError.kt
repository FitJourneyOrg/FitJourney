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
    if (e !is ResponseException) return AppError.Unexpected("Falha de rede", e)
    val body = runCatching { e.response.body<ErrorResponse>() }.getOrNull()
    val msg = body?.message
    return when (e.response.status) {
        HttpStatusCode.Unauthorized -> AppError.Unauthorized(msg ?: "Sessão expirada. Faça login novamente.")
        HttpStatusCode.Forbidden -> AppError.Forbidden(msg ?: "Sem permissão", body?.code)
        HttpStatusCode.BadRequest -> AppError.Validation(msg ?: "Dados inválidos", body?.fieldErrors ?: emptyMap())
        HttpStatusCode.NotFound -> AppError.NotFound(msg ?: "Não encontrado")
        HttpStatusCode.Conflict -> AppError.Conflict(msg ?: "Conflito de estado")
        else -> AppError.Unexpected(msg ?: "Falha na operação", e)
    }
}
