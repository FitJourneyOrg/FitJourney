package dev.rafael.server.error

import dev.rafael.contract.error.ErrorCodes
import dev.rafael.contract.error.ErrorResponse
import dev.rafael.core.result.AppError
import io.ktor.http.HttpStatusCode

fun AppError.toHttp(): Pair<HttpStatusCode, ErrorResponse> = when (this) {
    is AppError.Validation ->
        HttpStatusCode.BadRequest to ErrorResponse(ErrorCodes.VALIDATION, message, fieldErrors)
    is AppError.Unauthorized ->
        HttpStatusCode.Unauthorized to ErrorResponse(ErrorCodes.UNAUTHORIZED, message)
    is AppError.Forbidden ->
        HttpStatusCode.Forbidden to ErrorResponse(code ?: ErrorCodes.FORBIDDEN, message)
    is AppError.NotFound ->
        HttpStatusCode.NotFound to ErrorResponse(ErrorCodes.NOT_FOUND, message)
    is AppError.Conflict ->
        HttpStatusCode.Conflict to ErrorResponse(ErrorCodes.CONFLICT, message)
    // Unexpected: NÃO vaza `message`/`cause` pro cliente — genérico no fio, detalhe só no log.
    is AppError.Unexpected ->
        HttpStatusCode.InternalServerError to ErrorResponse(ErrorCodes.INTERNAL, "Erro interno")
}