package dev.rafael.server.features.notificacao.routes

import dev.rafael.contract.notificacao.NotificacaoDto
import dev.rafael.core.result.map
import dev.rafael.server.auth.FirebaseUser
import dev.rafael.server.error.respondResult
import dev.rafael.server.features.notificacao.models.Notificacao
import dev.rafael.server.features.notificacao.services.NotificacaoService
import dev.rafael.server.plugins.FIREBASE_AUTH
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post

/** A central de notificações (F.1) — o que o sininho da Home abre. */
fun Route.notificacaoRoutes(service: NotificacaoService) {
    authenticate(FIREBASE_AUTH) {

        /**
         * Minhas notificações, mais recente primeiro, teto de 100.
         *
         * **O contador de não-lidas sai desta MESMA lista** (`count { readAt == null }`), e não de
         * uma rota `/count`. Duas fontes da mesma verdade divergem no dia em que uma ganhar cache
         * e a outra não — é a mesma decisão do badge de pedidos do #35.
         */
        get("/me/notifications") {
            val p = call.principal<FirebaseUser>()!!
            call.respondResult(service.minhas(p.uid, p.email).map { it.map(Notificacao::toDto) })
        }

        /**
         * Marca TODAS como lidas. Chamada quando a central abre.
         *
         * Marcar uma a uma seria mais granular e pior: abrir a central é o gesto de "vi tudo
         * isto", e exigir um toque por item deixaria o sininho aceso sobre coisas já lidas.
         */
        post("/me/notifications/read") {
            val p = call.principal<FirebaseUser>()!!
            call.respondResult(service.marcarComoLidas(p.uid, p.email))
        }
    }
}

private fun Notificacao.toDto() = NotificacaoDto(
    id = id.toString(),
    type = tipo,
    title = titulo,
    body = corpo,
    data = dados,
    readAt = lidaEm?.toString(),
    createdAt = criadaEm.toString(),
)
