package dev.rafael.server

import dev.rafael.server.db.DatabaseFactory
import dev.rafael.server.features.user.routes.userRoutes
import dev.rafael.server.features.user.services.UserService
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.serialization.Serializable
import org.koin.ktor.ext.get
import org.koin.ktor.ext.inject     // ou get



@Serializable
data class HealthResponse(
    val status: String,
    val service: String = "fitjourney-server",
    val db: String,
)

fun Application.configureRouting() {
    val userService = get<UserService>()
    routing {
        userRoutes(userService)
        get("/health") {
            val dbOk = DatabaseFactory.isHealthy()
            call.respond(
                status = if (dbOk) HttpStatusCode.OK else HttpStatusCode.ServiceUnavailable,
                message = HealthResponse(
                    status = if (dbOk) "UP" else "DEGRADED",
                    db = if (dbOk) "UP" else "DOWN",
                ),
            )
        }
    }
}