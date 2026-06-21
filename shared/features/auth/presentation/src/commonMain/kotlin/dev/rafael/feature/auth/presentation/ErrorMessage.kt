package dev.rafael.feature.auth.presentation

import dev.rafael.core.result.AppError

/** Traduz AppError em mensagem amigável pro usuário. */
fun AppError.toMessage(): String = when (this) {
    is AppError.Unauthorized -> message
    is AppError.NotFound -> "Não encontrado"
    is AppError.Unexpected -> message
    else -> "Algo deu errado. Tente de novo."
}