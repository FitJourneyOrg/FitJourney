package dev.rafael.server.features.notificacao.routes

import dev.rafael.contract.notificacao.RegistrarDispositivoRequest
import dev.rafael.core.result.AppError
import dev.rafael.core.result.asFailure
import dev.rafael.core.result.flatMap
import dev.rafael.server.auth.FirebaseUser
import dev.rafael.server.error.respondResult
import dev.rafael.server.features.notificacao.db.DeviceTokenRepository
import dev.rafael.server.features.user.services.UserService
import dev.rafael.server.plugins.FIREBASE_AUTH
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.post
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

/**
 * Registro de aparelho para push (fatia F.1).
 *
 * Duas rotas, e uma assimetria importante entre elas.
 */
fun Route.deviceRoutes(service: UserService, tokens: DeviceTokenRepository) {
    authenticate(FIREBASE_AUTH) {

        /**
         * Registra ESTE aparelho para o usuário autenticado.
         *
         * Chamada no login e sempre que o FCM reemite o token — o que ele faz sozinho, sem avisar.
         * Idempotente: registrar o mesmo token duas vezes só atualiza `updated_at`.
         *
         * O `userId` NÃO vem no corpo: quem registra é quem está autenticado. Aceitá-lo do cliente
         * deixaria qualquer um redirecionar as notificações de outra pessoa para o próprio
         * aparelho — mesmo princípio do check-in.
         */
        post("/me/devices") {
            val p = call.principal<FirebaseUser>()!!
            val body = call.receive<RegistrarDispositivoRequest>()

            if (body.token.isBlank()) {
                return@post call.respondResult(AppError.Validation("Token vazio").asFailure())
            }

            val agora = Clock.System.now().toLocalDateTime(TimeZone.UTC)
            call.respondResult(
                service.findOrCreate(p.uid, p.email).flatMap { u ->
                    tokens.registrar(body.token, u.id, agora)
                },
            )
        }

        /**
         * Dá baixa no aparelho. **Chamada no LOGOUT.**
         *
         * A mais importante das duas e a mais fácil de esquecer: sem ela, quem sai da conta
         * continua recebendo notificações no aparelho até a próxima pessoa fazer login — e nesse
         * intervalo o push chega para quem não deveria ver.
         *
         * **Apaga pelo TOKEN, não pelo usuário.** Apagar todos os tokens do usuário derrubaria o
         * push nos OUTROS aparelhos dele, que continuam logados. Sair do celular não é sair do
         * tablet.
         *
         * Não verifica o dono do token de propósito: quem tem o token tem o aparelho na mão, e
         * apagar o registro alheio exigiria conhecer um token que não se tem. Errar para o lado
         * permissivo custa uma notificação a menos; para o outro lado, custa notificação para o
         * dono anterior do celular.
         */
        delete("/me/devices/{token}") {
            call.respondResult(tokens.apagar(listOf(call.parameters["token"].orEmpty())))
        }
    }
}
