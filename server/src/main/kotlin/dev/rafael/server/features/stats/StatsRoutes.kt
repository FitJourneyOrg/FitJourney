package dev.rafael.server.features.stats

import dev.rafael.server.auth.FirebaseUser
import dev.rafael.server.error.respondResult
import dev.rafael.server.plugins.FIREBASE_AUTH
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

fun Route.statsRoutes(service: StatsService, achievements: AchievementService) {
    authenticate(FIREBASE_AUTH) {
        // XP, nível e streak do usuário — derivados das sessões (ARCH #16).
        get("/me/stats") {
            val p = call.principal<FirebaseUser>()!!
            call.respondResult(service.forUser(p.uid, p.email))
        }

        // Catálogo COMPLETO de conquistas: as desbloqueadas com a data, as demais com o
        // progresso. A leitura também AVALIA e concede o que faltar — é o que dá retroativo
        // a quem já treinava antes da feature existir. Idempotente.
        get("/me/achievements") {
            val p = call.principal<FirebaseUser>()!!
            call.respondResult(achievements.forUser(p.uid, p.email))
        }
    }
}
