package dev.rafael.server.features.checkin.routes

import dev.rafael.contract.checkin.NovoComentarioRequest
import dev.rafael.contract.checkin.ReagirRequest
import dev.rafael.server.auth.FirebaseUser
import dev.rafael.server.error.respondResult
import dev.rafael.server.features.checkin.services.SocialService
import dev.rafael.server.plugins.FIREBASE_AUTH
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post

/**
 * Comentários e reações (fatia E.1).
 *
 * ## Por que tudo pendurado em `/groups/{id}`
 *
 * O grupo é a fronteira de acesso ([REGRA] #33), e a rota diz isso. Uma rota
 * `/checkins/{id}/comments` funcionaria e esconderia a regra: quem lê o caminho não veria que
 * pertencer ao grupo é condição para tudo. **A rota é onde a fronteira fica visível.**
 *
 * A exceção que confirma: `/checkins/{id}/foto` não tem o grupo no caminho, e por isso precisa
 * conferir filiação a cada leitura — foi a única rota da fatia B que exigiu explicação no KDoc.
 */
fun Route.socialRoutes(service: SocialService) {
    authenticate(FIREBASE_AUTH) {

        /** Os comentários de um check-in, do mais antigo para o mais novo. */
        get("/groups/{id}/checkins/{checkInId}/comments") {
            val p = call.principal<FirebaseUser>()!!
            call.respondResult(
                service.comentarios(
                    p.uid,
                    p.email,
                    call.parameters["id"].orEmpty(),
                    call.parameters["checkInId"].orEmpty(),
                ),
            )
        }

        /** Comentar. Sem edição (8.1) — não existe `PUT`, e a ausência é a regra. */
        post("/groups/{id}/checkins/{checkInId}/comments") {
            val p = call.principal<FirebaseUser>()!!
            val corpo = call.receive<NovoComentarioRequest>()
            call.respondResult(
                service.comentar(
                    p.uid,
                    p.email,
                    call.parameters["id"].orEmpty(),
                    call.parameters["checkInId"].orEmpty(),
                    corpo.body,
                ),
            )
        }

        /** Apagar comentário: o autor ou o admin (decisão de 2026-09-06). */
        delete("/groups/{id}/comments/{commentId}") {
            val p = call.principal<FirebaseUser>()!!
            call.respondResult(
                service.apagarComentario(
                    p.uid,
                    p.email,
                    call.parameters["id"].orEmpty(),
                    call.parameters["commentId"].orEmpty(),
                ),
            )
        }

        /**
         * Pôr ou TROCAR a reação (8.2).
         *
         * `POST` e não `PUT` porque a operação é "reagir", não "substituir o recurso reação" — e
         * quem chama não precisa saber se já existia uma. A troca é `ON CONFLICT DO UPDATE` no
         * banco, invisível daqui.
         */
        post("/groups/{id}/checkins/{checkInId}/reactions") {
            val p = call.principal<FirebaseUser>()!!
            val corpo = call.receive<ReagirRequest>()
            call.respondResult(
                service.reagir(
                    p.uid,
                    p.email,
                    call.parameters["id"].orEmpty(),
                    call.parameters["checkInId"].orEmpty(),
                    corpo.emoji,
                ),
            )
        }

        /** Tirar a minha reação. Sem corpo: só existe UMA minha, então não há o que identificar. */
        delete("/groups/{id}/checkins/{checkInId}/reactions") {
            val p = call.principal<FirebaseUser>()!!
            call.respondResult(
                service.desreagir(
                    p.uid,
                    p.email,
                    call.parameters["id"].orEmpty(),
                    call.parameters["checkInId"].orEmpty(),
                ),
            )
        }
    }
}
