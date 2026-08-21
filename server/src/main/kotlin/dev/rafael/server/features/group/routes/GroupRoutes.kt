package dev.rafael.server.features.group.routes

import dev.rafael.contract.group.CreateGroupRequest
import dev.rafael.contract.group.JoinByCodeRequest
import dev.rafael.server.auth.FirebaseUser
import dev.rafael.server.error.respondResult
import dev.rafael.server.features.group.services.GroupMembershipService
import dev.rafael.server.features.group.services.GroupService
import dev.rafael.server.plugins.FIREBASE_AUTH
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
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

/**
 * Entrada e papéis (fatia A.2).
 *
 * Continua tudo autenticado: "público" aqui significa **não precisa ser membro DO GRUPO**, não
 * "não precisa estar logado". Deixar o preview aberto a anônimos transformaria o token do
 * convite em endereço raspável.
 */
fun Route.groupMembershipRoutes(service: GroupMembershipService) {
    authenticate(FIREBASE_AUTH) {

        /**
         * O preview (2-B.0) — a única leitura que responde a quem não é membro.
         * `?code=ABC123` ou `?invite=<uuid>`. Uma rota e não duas: para a tela é o mesmo destino,
         * e o que muda é só como se chegou nele.
         */
        get("/groups/preview") {
            val p = call.principal<FirebaseUser>()!!
            call.respondResult(
                service.preview(
                    firebaseUid = p.uid,
                    email = p.email,
                    code = call.request.queryParameters["code"],
                    inviteToken = call.request.queryParameters["invite"],
                ),
            )
        }

        post("/groups/join") {
            val p = call.principal<FirebaseUser>()!!
            val body = call.receive<JoinByCodeRequest>()
            call.respondResult(service.entrarPorCodigo(p.uid, p.email, body.code))
        }

        post("/invites/{token}/join") {
            val p = call.principal<FirebaseUser>()!!
            call.respondResult(service.entrarPorConvite(p.uid, p.email, call.parameters["token"].orEmpty()))
        }

        post("/groups/{id}/leave") {
            val p = call.principal<FirebaseUser>()!!
            call.respondResult(service.sair(p.uid, p.email, call.parameters["id"].orEmpty()))
        }

        /** DELETE e não POST: expulsar é remover um recurso (o vínculo daquela pessoa). */
        delete("/groups/{id}/members/{userId}") {
            val p = call.principal<FirebaseUser>()!!
            call.respondResult(
                service.expulsar(
                    p.uid, p.email,
                    call.parameters["id"].orEmpty(),
                    call.parameters["userId"].orEmpty(),
                ),
            )
        }

        post("/groups/{id}/admin/{userId}") {
            val p = call.principal<FirebaseUser>()!!
            call.respondResult(
                service.transferirAdmin(
                    p.uid, p.email,
                    call.parameters["id"].orEmpty(),
                    call.parameters["userId"].orEmpty(),
                ),
            )
        }

        /** Gerar REVOGA o anterior: um convite ativo por grupo. */
        post("/groups/{id}/invite") {
            val p = call.principal<FirebaseUser>()!!
            call.respondResult(service.gerarConvite(p.uid, p.email, call.parameters["id"].orEmpty()))
        }

        delete("/groups/{id}/invite") {
            val p = call.principal<FirebaseUser>()!!
            call.respondResult(service.revogarConvite(p.uid, p.email, call.parameters["id"].orEmpty()))
        }
    }
}
