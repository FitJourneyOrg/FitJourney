package dev.rafael.server.features.user.routes

import dev.rafael.core.result.map
import dev.rafael.server.auth.FirebaseUser
import dev.rafael.server.error.respondResult
import dev.rafael.server.features.user.models.toDto
import dev.rafael.server.features.user.services.UserService
import dev.rafael.server.plugins.FIREBASE_AUTH
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

fun Route.userRoutes(service: UserService) {
    authenticate(FIREBASE_AUTH) {
        get("/me") {
            val principal = call.principal<FirebaseUser>()!!
            val result = service.findOrCreate(principal.uid, principal.email)
                .map { it.toDto() }          // User -> UserDto ANTES do respond
            call.respondResult(result)        // respondResult serializa UserDto direto
        }
    }
}