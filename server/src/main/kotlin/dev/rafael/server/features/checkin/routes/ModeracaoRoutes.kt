package dev.rafael.server.features.checkin.routes

import dev.rafael.contract.checkin.DenunciarRequest
import dev.rafael.contract.checkin.JulgarRequest
import dev.rafael.contract.checkin.ModerationBadgeDto
import dev.rafael.core.result.map
import dev.rafael.server.auth.FirebaseUser
import dev.rafael.server.error.respondResult
import dev.rafael.server.features.checkin.services.ModeracaoService
import dev.rafael.server.plugins.FIREBASE_AUTH
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post

/**
 * Denúncia e moderação (fatia E.2, seção 6).
 *
 * Tudo sob `/groups/{id}` pelo mesmo motivo da E.1: **o grupo é a fronteira de acesso e a rota diz
 * isso.** Aqui a razão fica ainda mais óbvia — o admin que julga é o admin *daquele* grupo, e uma
 * rota `/reports/{id}` esconderia de qual autoridade se está falando.
 *
 * ## Nenhum `DELETE`, e a ausência é o invariante
 *
 * Julgar é `POST`, mesmo quando o efeito é remover um comentário. Um `DELETE /reports/{id}` seria a
 * forma natural de "resolver" um caso, e traria junto a ideia errada: a decisão do admin não apaga
 * o pedido, ela **acrescenta um registro** (6.6). O verbo HTTP acompanha a semântica de negócio,
 * não o efeito colateral.
 */
fun Route.moderacaoRoutes(service: ModeracaoService) {
    authenticate(FIREBASE_AUTH) {

        /** 6.1: denunciar um check-in. Motivo obrigatório. */
        post("/groups/{id}/checkins/{checkInId}/reports") {
            val p = call.principal<FirebaseUser>()!!
            val corpo = call.receive<DenunciarRequest>()
            call.respondResult(
                service.denunciarCheckIn(
                    p.uid,
                    p.email,
                    call.parameters["id"].orEmpty(),
                    call.parameters["checkInId"].orEmpty(),
                    corpo.reason,
                ),
            )
        }

        /** 6.4: denunciar um comentário. */
        post("/groups/{id}/comments/{commentId}/reports") {
            val p = call.principal<FirebaseUser>()!!
            val corpo = call.receive<DenunciarRequest>()
            call.respondResult(
                service.denunciarComentario(
                    p.uid,
                    p.email,
                    call.parameters["id"].orEmpty(),
                    call.parameters["commentId"].orEmpty(),
                    corpo.reason,
                ),
            )
        }

        /**
         * A FILA do admin (6.2). Membro comum leva **403** — ele sabe que o grupo existe.
         *
         * Sem paginação: um grupo tem no máximo 50 membros, e uma fila com dezenas de casos abertos
         * já é o sintoma de um problema que paginar não resolve.
         */
        get("/groups/{id}/reports") {
            val p = call.principal<FirebaseUser>()!!
            call.respondResult(
                service.fila(p.uid, p.email, call.parameters["id"].orEmpty()),
            )
        }

        /**
         * O contador do badge. Rota separada da fila de propósito: a toolbar precisa do número a
         * cada abertura de tela, e baixar a fila inteira — com check-ins e comentários hidratados —
         * para exibir um "3" seria pagar a página inteira por um inteiro.
         */
        get("/groups/{id}/reports/count") {
            val p = call.principal<FirebaseUser>()!!
            call.respondResult(
                service.pendentes(p.uid, p.email, call.parameters["id"].orEmpty())
                    .map { ModerationBadgeDto(it) },
            )
        }

        /**
         * 6.3 + 6.6: o julgamento. O id da rota é o do **ALVO**, não o da denúncia.
         *
         * É o que faz "várias denúncias viram uma solicitação" (6.11) valer também aqui: o admin
         * decide sobre o conteúdo, e todas as denúncias abertas dele fecham juntas. Se a rota
         * apontasse para uma denúncia, julgar cinco vezes o mesmo check-in seria possível.
         */
        post("/groups/{id}/reports/{targetId}/judgment") {
            val p = call.principal<FirebaseUser>()!!
            val corpo = call.receive<JulgarRequest>()
            call.respondResult(
                service.julgar(
                    p.uid,
                    p.email,
                    call.parameters["id"].orEmpty(),
                    call.parameters["targetId"].orEmpty(),
                    corpo.acatar,
                ),
            )
        }

        /**
         * 6.10: o admin invalida direto, sem denúncia prévia.
         *
         * Rota própria porque não há caso a julgar — passar pelo `judgment` exigiria fabricar uma
         * denúncia falsa para poder julgá-la. A auditoria gravada é a mesma, e é o que mantém os
         * dois caminhos indistinguíveis para quem for ler o histórico.
         */
        post("/groups/{id}/checkins/{checkInId}/invalidate") {
            val p = call.principal<FirebaseUser>()!!
            call.respondResult(
                service.invalidarDireto(
                    p.uid,
                    p.email,
                    call.parameters["id"].orEmpty(),
                    call.parameters["checkInId"].orEmpty(),
                ),
            )
        }
    }
}
