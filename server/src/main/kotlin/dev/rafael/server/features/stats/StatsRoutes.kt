package dev.rafael.server.features.stats

import dev.rafael.contract.i18n.Idioma
import dev.rafael.contract.i18n.IdiomaPolicy
import dev.rafael.server.auth.FirebaseUser
import dev.rafael.server.error.respondResult
import dev.rafael.server.plugins.FIREBASE_AUTH
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.application.ApplicationCall
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

fun Route.statsRoutes(
    service: StatsService,
    achievements: AchievementService,
    progress: ProgressService,
) {
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

        /**
         * Analise de progressao (J.2). Responde 200 SEMPRE: os blocos pagos vem nulos para quem
         * e free, com `analysisLocked = true`.
         *
         * Nao usa 403 com codigo de portao como o `ProgramLimits` porque aqui nao ha acao
         * bloqueada - a tela mistura blocos gratis e pagos, e um erro que nao e erro custaria
         * uma segunda chamada e um estado de falha falso.
         */
        get("/me/progress") {
            val p = call.principal<FirebaseUser>()!!
            call.respondResult(progress.forUser(p.uid, p.email, call.idiomaPedido()))
        }
    }
}

/** Mesmo contrato das demais rotas que exibem catalogo: `?locale=`, nunca cabecalho (REGRA G.1). */
private fun ApplicationCall.idiomaPedido(): Idioma =
    IdiomaPolicy.de(request.queryParameters["locale"])
