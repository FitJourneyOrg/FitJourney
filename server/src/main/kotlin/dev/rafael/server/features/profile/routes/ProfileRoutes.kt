package dev.rafael.server.features.profile.routes

import dev.rafael.contract.profile.ProfileDto
import dev.rafael.server.auth.FirebaseUser
import dev.rafael.server.error.respondResult
import dev.rafael.server.features.profile.services.ProfileService
import dev.rafael.server.plugins.FIREBASE_AUTH
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.put

fun Route.profileRoutes(service: ProfileService) {
    authenticate(FIREBASE_AUTH) {
        get("/me/profile") {
            val principal = call.principal<FirebaseUser>()!!
            val result = service.getProfile(principal.uid, principal.email)
            call.respondResult(result)
        }
        put("/me/profile") {
            val principal = call.principal<FirebaseUser>()!!
            val dto = call.receive<ProfileDto>()
            val result = service.saveProfile(principal.uid, principal.email, dto)
            call.respondResult(result)
        }
    }
}