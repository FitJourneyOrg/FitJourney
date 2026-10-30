package dev.rafael.server.features.exercise.routes

import dev.rafael.server.auth.FirebaseUser
import dev.rafael.server.error.respondResult
import dev.rafael.server.features.exercise.services.PromotionService
import dev.rafael.server.plugins.FIREBASE_AUTH
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post

fun Route.promotionRoutes(service: PromotionService) {
    authenticate(FIREBASE_AUTH) {
        // Sempre 200 — a sugestão é derivada e vem `null` quando não há nenhuma (ver PromotionStatusDto).
        get("/me/promotion") {
            val principal = call.principal<FirebaseUser>()!!
            call.respondResult(service.status(principal.uid, principal.email))
        }

        // Sem corpo: o servidor recalcula a sugestão atual, nunca confia na que o cliente mandaria.
        post("/me/promotion/decline") {
            val principal = call.principal<FirebaseUser>()!!
            call.respondResult(service.decline(principal.uid, principal.email))
        }
    }
}
