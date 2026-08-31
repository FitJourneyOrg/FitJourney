package dev.rafael.server.features.friendship.routes

import dev.rafael.contract.friendship.FriendRequestDto
import dev.rafael.contract.friendship.PersonDto
import dev.rafael.core.result.map
import dev.rafael.server.auth.FirebaseUser
import dev.rafael.server.error.respondResult
import dev.rafael.server.features.friendship.models.PedidoRecebido
import dev.rafael.server.features.friendship.models.Pessoa
import dev.rafael.server.features.friendship.services.FriendshipService
import dev.rafael.server.plugins.FIREBASE_AUTH
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post

/**
 * Amizades e bloqueios (ARCH #35).
 *
 * ## Verbos: por que tanto POST e tão pouco PUT
 *
 * Aceitar, recusar e bloquear são ATOS, não edições de recurso. `POST /friends/{id}/accept` diz o
 * que aconteceu; `PUT /friends/{id}` com `{status: "ACEITA"}` deixaria o cliente escolher o
 * estado — inclusive um que ele não tem direito de escolher, como aceitar o próprio pedido. O
 * verbo estreito é a mesma ideia da porta estreita: quem não pode expressar, não pode errar.
 *
 * `DELETE` fica para as duas remoções que são mesmo remoções: desfazer amizade e desbloquear.
 * Cancelar um pedido enviado também é `DELETE` — a linha some de verdade.
 */
fun Route.friendshipRoutes(service: FriendshipService) {
    authenticate(FIREBASE_AUTH) {

        /** Meus amigos, alfabético. */
        get("/friends") {
            val p = call.principal<FirebaseUser>()!!
            call.respondResult(service.meusAmigos(p.uid, p.email).map { it.map(Pessoa::toDto) })
        }

        /**
         * Pedidos que EU recebi.
         *
         * Só os recebidos. Os que eu mandei aparecem no perfil da pessoa como "pedido enviado" —
         * uma caixa de saída de pedidos é tela que ninguém abre duas vezes, e cada tela a mais
         * é mais um lugar onde o contador pode divergir.
         */
        get("/friends/requests") {
            val p = call.principal<FirebaseUser>()!!
            call.respondResult(service.meusPedidos(p.uid, p.email).map { it.map(PedidoRecebido::toDto) })
        }

        /** Manda o pedido. O alvo vem por ID — quem tem só o código passa antes pelo resgate. */
        post("/friends/{id}") {
            val p = call.principal<FirebaseUser>()!!
            call.respondResult(service.pedir(p.uid, p.email, call.parameters["id"].orEmpty()))
        }

        post("/friends/{id}/accept") {
            val p = call.principal<FirebaseUser>()!!
            call.respondResult(service.aceitar(p.uid, p.email, call.parameters["id"].orEmpty()))
        }

        post("/friends/{id}/decline") {
            val p = call.principal<FirebaseUser>()!!
            call.respondResult(service.recusar(p.uid, p.email, call.parameters["id"].orEmpty()))
        }

        /**
         * Some com a relação: cancela o pedido que eu mandei OU desfaz a amizade.
         *
         * Uma rota para os dois porque, do ponto de vista de quem toca, é o mesmo gesto — "não
         * quero mais essa relação". Quem decide qual cabe é o ESTADO, no serviço; a tela não
         * precisa saber a diferença para chamar a rota certa.
         */
        delete("/friends/{id}") {
            val p = call.principal<FirebaseUser>()!!
            call.respondResult(service.remover(p.uid, p.email, call.parameters["id"].orEmpty()))
        }

        // ---- bloqueio ----

        /** Quem eu bloqueei. Alimenta Configurações da conta → Bloqueados. */
        get("/blocks") {
            val p = call.principal<FirebaseUser>()!!
            call.respondResult(service.meusBloqueados(p.uid, p.email).map { it.map(Pessoa::toDto) })
        }

        post("/blocks/{id}") {
            val p = call.principal<FirebaseUser>()!!
            call.respondResult(service.bloquear(p.uid, p.email, call.parameters["id"].orEmpty()))
        }

        delete("/blocks/{id}") {
            val p = call.principal<FirebaseUser>()!!
            call.respondResult(service.desbloquear(p.uid, p.email, call.parameters["id"].orEmpty()))
        }
    }
}

private fun Pessoa.toDto() = PersonDto(userId = userId.toString(), displayName = displayName)

private fun PedidoRecebido.toDto() = FriendRequestDto(
    from = de.toDto(),
    createdAt = createdAt.toString(),
)
