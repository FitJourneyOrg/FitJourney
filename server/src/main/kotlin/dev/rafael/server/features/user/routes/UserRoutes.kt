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
import io.ktor.server.routing.post

fun Route.userRoutes(service: UserService) {
    authenticate(FIREBASE_AUTH) {
        get("/me") {
            val principal = call.principal<FirebaseUser>()!!
            val result = service.findOrCreate(principal.uid, principal.email)
                .map { it.toDto() }          // User -> UserDto ANTES do respond
            call.respondResult(result)        // respondResult serializa UserDto direto
        }

        // Assinatura (Fase 7). DEV: liga o premium direto (compra simulada). A compra REAL
        // (Play Billing) roda no cliente e chama isto; a verificação de recibo entra aqui depois.
        post("/me/subscribe") {
            val principal = call.principal<FirebaseUser>()!!
            val result = service.activatePremium(principal.uid, principal.email)
                .map { it.toDto() }
            call.respondResult(result)
        }
    }
}