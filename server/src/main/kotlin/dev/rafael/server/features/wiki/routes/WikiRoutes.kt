package dev.rafael.server.features.wiki.routes

import dev.rafael.contract.i18n.Idioma
import dev.rafael.contract.i18n.IdiomaPolicy
import dev.rafael.server.error.respondResult
import dev.rafael.server.features.wiki.services.WikiService
import dev.rafael.server.plugins.FIREBASE_AUTH
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.authenticate
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

/**
 * O idioma pedido pelo cliente, ou o piso. Cópia deliberada da extensão homônima do
 * `ExerciseRoutes`: **feature nunca depende de feature**, e compartilhar uma linha de
 * `queryParameters` não paga o acoplamento (mesmo julgamento que manteve o POST de ativação no
 * `ProgramDataSource` em vez de o módulo `workout` depender de `program`).
 *
 * `IdiomaPolicy.de` e não `valida`, pela [REGRA] da G.1: *entrada de sistema tolera, entrada de
 * usuário recusa*. Quem manda a tag é o app, e um app antigo pedindo um idioma que este servidor
 * ainda não conhece tem de receber o acervo **no piso**, nunca um 400 — a tela ficaria vazia por
 * causa de uma preferência.
 *
 * ⚠️ O cliente **manda** o idioma em vez de o servidor ler `users.locale`, que a V47 já guarda, e a
 * razão está escrita no `ExerciseRoutes`: o `users.locale` é cópia reconciliada numa direção só e
 * pode estar atrasada, então o servidor devolveria o idioma antigo enquanto o cliente carimbaria o
 * cache com o novo. *Quem cacheia a resposta tem de ser quem escolhe a pergunta.*
 */
private fun ApplicationCall.idiomaPedido(): Idioma =
    IdiomaPolicy.de(request.queryParameters["locale"])

/**
 * ⭐ **[ARCH] O acervo é GRÁTIS para qualquer usuário autenticado. Não há gate de premium aqui, e a
 * ausência é decisão, não esquecimento.**
 *
 * Todo outro conteúdo do app tem história de entitlement (ARCH #23: o Dia 2 do programa de IA
 * tranca, o teto de programas tranca, editar conteúdo de IA tranca), então quem chegar depois vai
 * estranhar este recurso sem `requireReadable`. A razão:
 *
 * > **O paywall protege o motor, não o conhecimento.**
 *
 * O alvo declarado deste conteúdo é quem está começando e não tem base — que é, por definição, o
 * usuário AINDA NÃO convertido. Trancar o "guia do iniciante" atrás do premium significa que só
 * quem já pagou pode ler o texto escrito para quem ainda não pagou: é o funil ao contrário, porque
 * o que converte neste app é a pessoa criar hábito e então querer mais programas.
 *
 * O tier premium natural são os artigos científicos da V2 — conteúdo NOVO, cobrado por profundidade.
 * Trancar depois o que já nasceu grátis é o único movimento que realmente queima usuário.
 */
fun Route.wikiRoutes(service: WikiService) {
    authenticate(FIREBASE_AUTH) {
        get("/wiki") {
            call.respondResult(service.list(call.idiomaPedido()))
        }
    }
}
