package dev.rafael.server.features.exercise.routes

import dev.rafael.contract.exercise.ExerciseCategory
import dev.rafael.contract.i18n.Idioma
import dev.rafael.contract.i18n.IdiomaPolicy
import dev.rafael.core.result.AppError
import dev.rafael.core.result.asFailure
import dev.rafael.core.result.flatMap
import dev.rafael.server.auth.FirebaseUser
import dev.rafael.server.error.respondResult
import dev.rafael.server.features.exercise.services.ExerciseService
import dev.rafael.server.features.profile.services.ProfileService
import dev.rafael.server.plugins.FIREBASE_AUTH
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlin.uuid.Uuid
import dev.rafael.contract.error.ErrorCodes

/**
 * O idioma pedido pelo cliente, ou o piso.
 *
 * ## `IdiomaPolicy.de`, e NÃO `valida` — a distinção é [REGRA] da G.1
 *
 * *"Entrada de sistema tolera, entrada de usuário recusa."* Isto é entrada de sistema: quem manda
 * a tag é o app, não uma pessoa digitando. Um app antigo pedindo `pt` ou um idioma que este
 * servidor ainda não conhece tem de receber o catálogo **no piso**, nunca um 400 — a tela ficaria
 * vazia por causa de uma preferência.
 *
 * O `de` nunca falha: tenta exato, depois só a língua ignorando a região (`pt-PT` cai em `pt-BR`),
 * depois o `PADRAO`. Sem o parâmetro, também o piso.
 *
 * ⚠️ **Por que o cliente MANDA em vez de o servidor ler `users.locale`**, que a V47 já guarda: o
 * `users.locale` é cópia reconciliada na abertura, numa direção só. Se o `PATCH /me` falhou sem
 * rede, ele está atrasado — e o servidor devolveria o idioma antigo enquanto o cliente carimbaria
 * o cache com o novo. O cliente pede exatamente o que vai guardar.
 *
 * > **Quem cacheia a resposta tem de ser quem escolhe a pergunta.**
 */
// `request.queryParameters` e não `queryParameters`: o segundo é membro do `RoutingCall`, que só
// existe dentro do handler. Numa extensão de `ApplicationCall` ele não resolve, e o Kotlin vai
// procurar `get` em outro lugar — a primeira tentativa compilou contra `MatchGroup`.
private fun ApplicationCall.idiomaPedido(): Idioma =
    IdiomaPolicy.de(request.queryParameters["locale"])

fun Route.exerciseRoutes(service: ExerciseService, profileService: ProfileService) {
    authenticate(FIREBASE_AUTH) {
        get("/exercises") {
            val idioma = call.idiomaPedido()
            val categoryParam = call.queryParameters["category"]
            val result = if (categoryParam != null) {
                val category = runCatching { ExerciseCategory.valueOf(categoryParam) }.getOrNull()
                if (category == null) service.listAll(idioma) else service.listByCategory(category, idioma)
            } else {
                service.listAll(idioma)
            }
            call.respondResult(result)
        }

        // Alternativas de mesmo tipo pra troca (usa ambiente/nível/limitações do perfil).
        get("/exercises/{id}/alternatives") {
            val principal = call.principal<FirebaseUser>()!!
            val id = call.parameters["id"]?.let { runCatching { Uuid.parse(it) }.getOrNull() }
            val result = if (id == null) {
                AppError.Validation("Não consegui abrir este exercício.", code = ErrorCodes.ID_DE_EXERCICIO_INVALIDO).asFailure()
            } else {
                profileService.getProfile(principal.uid, principal.email).flatMap { p ->
                    val env = p.environment
                    if (env == null) AppError.Validation("Ambiente de treino não definido", code = ErrorCodes.AMBIENTE_NAO_DEFINIDO).asFailure()
                    else service.alternatives(id, env, p.level, p.limitations, call.idiomaPedido())
                }
            }
            call.respondResult(result)
        }
    }
}
