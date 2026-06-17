package dev.rafael.server.error

import dev.rafael.core.result.AppResult
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import dev.rafael.server.error.toHttp

suspend inline fun <reified T : Any> ApplicationCall.respondResult(
    result: AppResult<T>,
    successStatus: HttpStatusCode = HttpStatusCode.OK,
) {
    when (result) {
        is AppResult.Success -> respond(successStatus, result.value)
        is AppResult.Failure -> {
            val (status, body) = result.error.toHttp()
            respond(status, body)
        }
    }
}