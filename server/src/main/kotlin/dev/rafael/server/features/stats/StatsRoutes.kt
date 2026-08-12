package dev.rafael.server.features.stats

import dev.rafael.server.auth.FirebaseUser
import dev.rafael.server.error.respondResult
import dev.rafael.server.plugins.FIREBASE_AUTH
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

fun Route.statsRoutes(service: StatsService) {
    authenticate(FIREBASE_AUTH) {
        // XP, nível e streak do usuário — derivados das sessões (ARCH #16).
        get("/me/stats") {
            val p = call.principal<FirebaseUser>()!!
            call.respondResult(service.forUser(p.uid, p.email))
        }
    }
}
