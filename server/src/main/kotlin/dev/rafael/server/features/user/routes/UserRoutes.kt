package dev.rafael.server.features.user.routes

import dev.rafael.contract.user.UpdateMeRequest
import dev.rafael.core.result.map
import dev.rafael.server.auth.FirebaseUser
import dev.rafael.server.error.respondResult
import dev.rafael.server.features.user.models.toDto
import dev.rafael.server.features.user.services.PublicProfileService
import dev.rafael.server.features.user.services.UserService
import dev.rafael.server.plugins.FIREBASE_AUTH
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post

fun Route.userRoutes(service: UserService, perfis: PublicProfileService) {
    authenticate(FIREBASE_AUTH) {
        /**
         * Perfil público de terceiro (C.1, #34 + emenda 9.3-A).
         *
         * **Fica ANTES do `/me`** de propósito? Não — e é por isso que a ordem aqui não importa:
         * o Ktor prefere segmento literal a parâmetro, então `/users/me` nunca cairia neste
         * `{id}`. Registro a razão porque é o tipo de coisa que alguém "conserta" reordenando.
         *
         * Autenticada, e não pública: a 9.3-A abriu o perfil a **qualquer usuário autenticado**,
         * não à internet. Sem o `authenticate` isto viraria um raspador de perfis sem custo.
         */
        get("/users/{id}/profile") {
            val principal = call.principal<FirebaseUser>()!!
            val id = call.parameters["id"].orEmpty()
            call.respondResult(perfis.porId(principal.uid, principal.email, id))
        }

        get("/me") {
            val principal = call.principal<FirebaseUser>()!!
            val result = service.findOrCreate(principal.uid, principal.email)
                .map { it.toDto() }          // User -> UserDto ANTES do respond
            call.respondResult(result)        // respondResult serializa UserDto direto
        }

        /**
         * Renomeia o usuário (V35, ARCH #33/#34). Devolve o `UserDto` inteiro, não só o nome:
         * o cliente guarda o `/me` em cache, e devolver o recurso completo deixa a resposta
         * substituir a cópia local sem uma segunda requisição.
         *
         * Sem gate de premium — o nome é identidade, não recurso pago (#25).
         */
        patch("/me") {
            val principal = call.principal<FirebaseUser>()!!
            val body = call.receive<UpdateMeRequest>()
            val result = service
                .updateDisplayName(principal.uid, principal.email, body.displayName)
                .map { it.toDto() }
            call.respondResult(result)
        }

        // Assinatura (Fase 7). DEV: liga o premium direto (compra simulada). A compra REAL
        // (Play Billing) roda no cliente e chama isto; a verificação de recibo entra aqui depois.
        post("/me/subscribe") {
            val principal = call.principal<FirebaseUser>()!!
            val result = service.activatePremium(principal.uid, principal.email)
                .map { it.toDto() }
            call.respondResult(result)
        }
    }
}