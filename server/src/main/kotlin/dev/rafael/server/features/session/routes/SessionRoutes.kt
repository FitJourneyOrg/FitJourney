package dev.rafael.server.features.session.routes

import dev.rafael.contract.session.WorkoutSessionDto
import dev.rafael.server.auth.FirebaseUser
import dev.rafael.server.error.respondResult
import dev.rafael.server.features.session.services.SessionService
import dev.rafael.server.plugins.FIREBASE_AUTH
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post

fun Route.sessionRoutes(service: SessionService) {
    authenticate(FIREBASE_AUTH) {
        // Grava a sessão executada (idempotente por id — sync offline seguro).
        post("/sessions") {
            val p = call.principal<FirebaseUser>()!!
            val dto = call.receive<WorkoutSessionDto>()
            call.respondResult(service.record(p.uid, p.email, dto))
        }

        // Histórico do usuário.
        get("/sessions") {
            val p = call.principal<FirebaseUser>()!!
            call.respondResult(service.list(p.uid, p.email))
        }
    }
}
