package dev.rafael.server.features.group.routes

import dev.rafael.contract.group.CreateGroupRequest
import dev.rafael.server.auth.FirebaseUser
import dev.rafael.server.error.respondResult
import dev.rafael.server.features.group.services.GroupService
import dev.rafael.server.plugins.FIREBASE_AUTH
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post

/**
 * Grupos (ARCH #33, fatia A.1). Só criar e ler.
 *
 * Entrar por link/código é a A.2, e é lá que nasce a única rota desta fase que responde a quem
 * NÃO é membro (o preview do convite). Aqui tudo exige vínculo.
 */
fun Route.groupRoutes(service: GroupService) {
    authenticate(FIREBASE_AUTH) {

        post("/groups") {
            val p = call.principal<FirebaseUser>()!!
            val body = call.receive<CreateGroupRequest>()
            call.respondResult(service.criar(p.uid, p.email, body))
        }

        /** Os grupos DE QUEM PEDIU. Não existe "listar todos os grupos" — não é um diretório. */
        get("/groups") {
            val p = call.principal<FirebaseUser>()!!
            call.respondResult(service.meusGrupos(p.uid, p.email))
        }

        get("/groups/{id}") {
            val p = call.principal<FirebaseUser>()!!
            val id = call.parameters["id"].orEmpty()
            call.respondResult(service.porId(p.uid, p.email, id))
        }
    }
}
