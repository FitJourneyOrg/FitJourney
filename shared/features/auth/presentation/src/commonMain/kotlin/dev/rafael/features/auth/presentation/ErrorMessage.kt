package dev.rafael.features.auth.presentation

import dev.rafael.core.result.AppError

/** Traduz AppError em mensagem amigável pro usuário. */
fun AppError.toMessage(): String = when (this) {
    is AppError.Unauthorized -> message
    is AppError.NotFound -> "Não encontrado"
    // Transporte: aqui não dá pra saber se é o wifi ou o servidor — quem sabe é a UI, que
    // consulta o sistema. Este texto é o fallback neutro (ver ErrorUi, fatia 2).
    is AppError.Connection -> message
    is AppError.Unexpected -> message
    else -> "Algo deu errado. Tente de novo."
}