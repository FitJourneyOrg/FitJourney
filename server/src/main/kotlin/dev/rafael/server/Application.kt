package dev.rafael.server

import dev.rafael.contract.error.ErrorCodes
import dev.rafael.contract.error.ErrorResponse
import dev.rafael.core.result.AppError
import dev.rafael.server.auth.FirebaseAdmin
import dev.rafael.server.db.DatabaseFactory
import dev.rafael.server.error.toHttp
import io.ktor.serialization.kotlinx.json.json
import androidx.annotation.Nullable
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.netty.EngineMain
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.defaultheaders.DefaultHeaders
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respondText
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.log
import io.ktor.server.response.respond
import org.slf4j.event.Level

fun main(args: Array<String>) = EngineMain.main(args)

fun Application.module() {

    DatabaseFactory.init(environment.config)
    FirebaseAdmin.init(environment.config)
    monitor.subscribe(ApplicationStopped) { DatabaseFactory.close() }

    configureSerialization()
    configureMonitoring()
    configureStatusPages()
    configureRouting()
}

private fun Application.configureSerialization() {
    install(ContentNegotiation) { json() }
}

private fun Application.configureMonitoring() {
    install(DefaultHeaders)
    install(CallLogging) {
        level = Level.INFO
        filter { it.request.local.uri != "/health" }   // não polui o log com o ping de health
    }
}

// Skeleton: catch-all genérico. No 1.2 isto vira o mapeamento AppError -> HTTP
// com o envelope de erro vindo de shared-contract.
private fun Application.configureStatusPages() {
    install(StatusPages) {
        // Exceção não prevista -> 500 genérico, stacktrace só no log.
        exception<Throwable> { call, cause ->
            call.application.log.error("Erro não tratado em ${call.request.local.uri}", cause)
            val (status, body) = AppError.Unexpected(cause = cause).toHttp()
            call.respond(status, body)
        }
        // 404 de rota não casada -> mesmo envelope, p/ consistência da API.
        status(HttpStatusCode.NotFound) { call, _ ->
            call.respond(
                HttpStatusCode.NotFound,
                ErrorResponse(ErrorCodes.NOT_FOUND, "Recurso não encontrado"),
            )
        }
    }
}