package dev.rafael.core.result

/** Erros de domínio. Kotlin puro — não conhece HTTP, Ktor nem serialização. */
sealed interface AppError {
    val message: String

    data class Validation(
        override val message: String = "Dados inválidos",
        val fieldErrors: Map<String, String> = emptyMap(),
    ) : AppError

    data class Unauthorized(override val message: String = "Não autenticado") : AppError
    data class Forbidden(
        override val message: String = "Sem permissão",
        val code: String? = null,
    ) : AppError
    data class NotFound(override val message: String = "Não encontrado") : AppError
    /**
     * 409. **Regra de negócio recusando**, não dado velho.
     *
     * Todo 409 desta API é uma recusa deliberada — "transfira o cargo antes de sair", "o desafio
     * já começou". Nenhum deles melhora com um retry, e é por isso que a UI não oferece um.
     *
     * `code` espelha o [Forbidden]: quando a tela quiser escrever a própria frase (#31), ela tem
     * o motivo em forma de máquina em vez de comparar texto.
     */
    data class Conflict(
        override val message: String = "Conflito de estado",
        val code: String? = null,
    ) : AppError

    /**
     * TRANSPORTE: não deu pra falar com o servidor — sem rede, servidor fora do ar, timeout,
     * DNS, 502/503/504. É o único erro em que servir cache local é correto: o servidor não
     * disse nada, então o último dado conhecido continua sendo a melhor verdade disponível.
     *
     * Separado de [Unexpected] de propósito. Antes os dois eram a mesma coisa, e isso causava
     * dois defeitos: 500 do servidor caía no fallback de cache como se fosse falta de rede, e
     * a UI dizia "sem conexão" com o wifi ligado.
     *
     * NÃO diz se o aparelho está offline ou se o servidor morreu — isso é fato de plataforma
     * (ConnectivityManager), resolvido na camada de UI. Aqui o domínio segue Kotlin puro.
     *
     * Só o CLIENTE produz este erro; o servidor nunca o devolve.
     */
    data class Connection(
        override val message: String = "Não foi possível conectar",
        val cause: Throwable? = null,
    ) : AppError

    /** Falha inesperada. `cause` é só p/ log no server — NUNCA vai pro fio. */
    data class Unexpected(
        override val message: String = "Erro interno",
        val cause: Throwable? = null,
    ) : AppError
}