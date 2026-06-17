package dev.rafael.core.result

/** Erros de domínio. Kotlin puro — não conhece HTTP, Ktor nem serialização. */
sealed interface AppError {
    val message: String

    data class Validation(
        override val message: String = "Dados inválidos",
        val fieldErrors: Map<String, String> = emptyMap(),
    ) : AppError

    data class Unauthorized(override val message: String = "Não autenticado") : AppError
    data class Forbidden(override val message: String = "Sem permissão") : AppError
    data class NotFound(override val message: String = "Não encontrado") : AppError
    data class Conflict(override val message: String = "Conflito de estado") : AppError

    /** Falha inesperada. `cause` é só p/ log no server — NUNCA vai pro fio. */
    data class Unexpected(
        override val message: String = "Erro interno",
        val cause: Throwable? = null,
    ) : AppError
}