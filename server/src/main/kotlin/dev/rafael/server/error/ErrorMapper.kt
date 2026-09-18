package dev.rafael.server.error

import dev.rafael.contract.error.ErrorCodes
import dev.rafael.contract.error.ErrorResponse
import dev.rafael.core.result.AppError
import io.ktor.http.HttpStatusCode

/**
 * `AppError` do domínio para a resposta HTTP.
 *
 * ## O `code` de cada família agora sai daqui (G.2, ARCH #37)
 *
 * Todas as famílias passaram a ter `code` opcional, e o padrão é sempre o mesmo: **o específico
 * quando existe, o genérico da família quando não**. O genérico não é preguiça: é o que dá ao
 * cliente antigo um texto para um código que ele ainda não conhece.
 *
 * ## A `message` continua no fio, e deixou de ser o que a pessoa lê
 *
 * O cliente escolhe o texto pelo `code`. A `message` fica como **diagnóstico** (aparece no log de
 * quem depura) e como **último fallback**, para o caso de o cliente não reconhecer nem o código
 * específico nem ter texto para o genérico.
 *
 * Tirá-la do fio economizaria bytes e custaria a única pista legível que sobra quando algo
 * inesperado chega ao aparelho de alguém.
 */
fun AppError.toHttp(): Pair<HttpStatusCode, ErrorResponse> = when (this) {
    is AppError.Validation ->
        HttpStatusCode.BadRequest to ErrorResponse(code ?: ErrorCodes.VALIDATION, message, fieldErrors)
    is AppError.Unauthorized ->
        HttpStatusCode.Unauthorized to ErrorResponse(code ?: ErrorCodes.UNAUTHORIZED, message)
    is AppError.Forbidden ->
        HttpStatusCode.Forbidden to ErrorResponse(code ?: ErrorCodes.FORBIDDEN, message)
    is AppError.NotFound ->
        HttpStatusCode.NotFound to ErrorResponse(code ?: ErrorCodes.NOT_FOUND, message)
    is AppError.Conflict ->
        HttpStatusCode.Conflict to ErrorResponse(code ?: ErrorCodes.CONFLICT, message)
    // Unexpected: NÃO vaza `message`/`cause` pro cliente — genérico no fio, detalhe só no log.
    // O cliente também ignora este texto (ver `ErrorUi`), então ele nunca chega a uma tela e não
    // precisa de tradução.
    is AppError.Unexpected ->
        HttpStatusCode.InternalServerError to ErrorResponse(ErrorCodes.INTERNAL, "Erro interno")
    // Connection é erro de CLIENTE (não consegui falar com o servidor). Se aparecer aqui, algo
    // interno se enganou de tipo — trata como falha interna e o log do StatusPages mostra onde.
    is AppError.Connection ->
        HttpStatusCode.InternalServerError to ErrorResponse(ErrorCodes.INTERNAL, "Erro interno")
}
