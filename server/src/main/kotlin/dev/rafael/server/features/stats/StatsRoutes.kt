package dev.rafael.server.features.stats

import dev.rafael.contract.error.ErrorCodes
import dev.rafael.contract.i18n.Idioma
import dev.rafael.contract.i18n.IdiomaPolicy
import dev.rafael.core.result.AppError
import dev.rafael.core.result.AppResult
import dev.rafael.core.result.asFailure
import dev.rafael.core.result.asSuccess
import dev.rafael.server.auth.FirebaseUser
import dev.rafael.server.error.respondResult
import dev.rafael.server.plugins.FIREBASE_AUTH
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.application.ApplicationCall
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlin.uuid.Uuid

fun Route.statsRoutes(
    service: StatsService,
    achievements: AchievementService,
    progress: ProgressService,
) {
    authenticate(FIREBASE_AUTH) {
        // XP, nível e streak do usuário — derivados das sessões (ARCH #16).
        get("/me/stats") {
            val p = call.principal<FirebaseUser>()!!
            call.respondResult(service.forUser(p.uid, p.email))
        }

        // Catálogo COMPLETO de conquistas: as desbloqueadas com a data, as demais com o
        // progresso. A leitura também AVALIA e concede o que faltar — é o que dá retroativo
        // a quem já treinava antes da feature existir. Idempotente.
        get("/me/achievements") {
            val p = call.principal<FirebaseUser>()!!
            call.respondResult(achievements.forUser(p.uid, p.email))
        }

        /**
         * Analise de progressao (J.2). Responde 200 SEMPRE: os blocos pagos vem nulos para quem
         * e free, com `analysisLocked = true`.
         *
         * Nao usa 403 com codigo de portao como o `ProgramLimits` porque aqui nao ha acao
         * bloqueada - a tela mistura blocos gratis e pagos, e um erro que nao e erro custaria
         * uma segunda chamada e um estado de falha falso.
         */
        get("/me/progress") {
            val p = call.principal<FirebaseUser>()!!
            when (val f = call.filtroPedido()) {
                is AppResult.Failure -> call.respondResult(f)
                is AppResult.Success -> call.respondResult(
                    progress.forUser(p.uid, p.email, call.idiomaPedido(), f.value, call.exerciciosPedidos()),
                )
            }
        }
    }
}

/** O valor de `?programId=` que pede as sessoes FORA de programa. */
private const val AVULSOS = "avulsos"

/**
 * `?exercicios=uuid,uuid` — quais ganham linha no grafico de 1RM.
 *
 * ⚠️ Id malformado e DESCARTADO, nao 400 — ao contrario do `programId`. A regra e a mesma dos
 * dois lados: `programId` muda QUAL dado responde, entao id errado ali e erro de verdade; a
 * selecao so muda o que e desenhado, e o `strengthTrend` da resposta ja diz quais entraram.
 * Recusar a requisicao inteira por um id ruim deixaria a tela sem grafico nenhum.
 */
private fun ApplicationCall.exerciciosPedidos(): List<Uuid> =
    request.queryParameters["exercicios"]
        ?.split(',')
        ?.mapNotNull { runCatching { Uuid.parse(it.trim()) }.getOrNull() }
        ?.distinct()
        .orEmpty()

/**
 * O recorte pedido na query: `?programId=<uuid|avulsos>&de=<n>&ate=<n>`.
 *
 * ⚠️ **Id malformado vira 400, nao "todos".** Cair no padrao em silencio mostraria um grafico
 * que ninguem pediu, com cara de resposta certa — e o cliente nunca saberia que mandou lixo.
 * O codigo ja existe desde a G.2.
 *
 * `de`/`ate` fora de ordem ou fora do programa NAO sao erro: o servico os encaixa na janela
 * real. Faixa e ajuste de visualizacao, e corrigir em silencio ali e o certo; id errado e outra
 * coisa, porque muda QUAL dado responde.
 */
private fun ApplicationCall.filtroPedido(): AppResult<ProgressService.Filtro> {
    // Janela fora da lista nao e 400: o servico encaixa no padrao. Janela e VISUALIZACAO, e
    // cliente velho pedindo 12 nao pode receber erro por isso. Id de programa invalido continua
    // sendo 400, porque esse muda QUAL dado responde.
    val semanas = request.queryParameters["semanas"]?.toIntOrNull() ?: ProgressService.JANELA_PADRAO
    val bruto = request.queryParameters["programId"]
        ?: return ProgressService.Filtro.Todos(semanas).asSuccess()
    if (bruto == AVULSOS) return ProgressService.Filtro.Avulsos(semanas).asSuccess()

    val id = runCatching { Uuid.parse(bruto) }.getOrNull()
        ?: return AppError.Validation(
            message = "Identificador de programa inválido.",
            // ⚠️ O 2o parametro posicional de Validation e `fieldErrors`, nao o codigo. Nomear
            // evita o erro que o compilador so pegou porque os tipos diferem -- se os dois
            // fossem String, teria compilado com o codigo virando mensagem de campo.
            code = ErrorCodes.ID_DE_PROGRAMA_INVALIDO,
        ).asFailure()

    return ProgressService.Filtro.DoPrograma(
        programId = id,
        de = request.queryParameters["de"]?.toIntOrNull(),
        ate = request.queryParameters["ate"]?.toIntOrNull(),
    ).asSuccess()
}

/** Mesmo contrato das demais rotas que exibem catalogo: `?locale=`, nunca cabecalho (REGRA G.1). */
private fun ApplicationCall.idiomaPedido(): Idioma =
    IdiomaPolicy.de(request.queryParameters["locale"])
