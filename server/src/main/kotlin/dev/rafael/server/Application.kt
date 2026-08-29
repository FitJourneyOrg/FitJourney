package dev.rafael.server

import dev.rafael.contract.error.ErrorCodes
import dev.rafael.contract.error.ErrorResponse
import dev.rafael.core.result.AppError
import dev.rafael.server.auth.FirebaseAdmin
import dev.rafael.server.db.DatabaseFactory
import dev.rafael.server.error.toHttp
import dev.rafael.server.features.notificacao.services.NotificacaoService
import dev.rafael.server.plugins.configureAuthentication
import dev.rafael.server.plugins.configureKoin
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.netty.EngineMain
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.defaultheaders.DefaultHeaders
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.http.HttpStatusCode
import dev.rafael.server.media.PurgaDeMidia
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.log
import io.ktor.server.response.respond
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.koin.ktor.ext.get
import org.slf4j.event.Level
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes

fun main(args: Array<String>) = EngineMain.main(args)

fun Application.module() {
    DatabaseFactory.init(environment.config)
    FirebaseAdmin.init(environment.config)
    monitor.subscribe(ApplicationStopped) { DatabaseFactory.close() }
    configureKoin()              // <- novo, cedo
    configureSerialization()
    configureMonitoring()
    configureAuthentication()    // <- novo, antes do routing
    configureStatusPages()
    configureRouting()           // já existe (HealthRoutes) — vou adicionar /me aqui
    agendarPurgaDeMidia()
    agendarPurgaDeNotificacoes()
}

/**
 * Varredor de mídia (4.8, emendada): acorda uma vez por dia e apaga foto sem dono.
 *
 * **No ciclo de vida da aplicação, e não numa `Thread` solta.** Usar o escopo do Ktor faz o laço
 * morrer junto com o servidor; uma thread própria sobreviveria ao `stop()` e seguraria o processo
 * na hora do desligamento — e em teste, entre um caso e outro.
 *
 * **Espera antes da primeira passada.** Subir o servidor e imediatamente varrer disco competiria
 * com as primeiras requisições, que são as que o usuário está esperando.
 *
 * **Com mais de uma instância, roda várias vezes.** Como apagar é idempotente, isso é desperdício
 * e não erro. No dia em que houver réplicas, a saída é tirar o varredor daqui e rodá-lo como
 * processo próprio — e nada do `PurgaDeMidia` muda, só quem o chama.
 */
private fun Application.agendarPurgaDeMidia() {
    val purga = get<PurgaDeMidia>()
    launch {
        delay(2.minutes)
        while (isActive) {
            runCatching { purga.rodar() }
                .onSuccess { if (it.fezAlgo) log.info("Purga de mídia: $it") }
                // Falhar não pode derrubar o laço: um erro de disco hoje não deve significar que
                // a purga nunca mais roda até alguém reiniciar o servidor.
                .onFailure { log.warn("Purga de mídia falhou; tenta de novo no próximo ciclo", it) }
            delay(PurgaDeMidia.INTERVALO)
        }
    }
}

/**
 * Purga de notificações com mais de 6 meses (F.1).
 *
 * **Laço próprio, e não um passo dentro da purga de mídia.** Juntar os dois pareceria economia e
 * criaria um acoplamento sem motivo: uma falha lendo o disco impediria a limpeza do banco, que
 * não tem nada a ver. Duas coisas que falham por razões diferentes falham separadas.
 *
 * O intervalo é o mesmo (1 dia) porque a natureza é a mesma: dado que se acumula devagar e cujo
 * corte não tem pressa.
 */
private fun Application.agendarPurgaDeNotificacoes() {
    val servico = get<NotificacaoService>()
    launch {
        // Escalonado em relação à purga de mídia: as duas rodando juntas no boot competiriam por
        // conexão do pool num momento em que o app ainda está atendendo as primeiras requisições.
        delay(5.minutes)
        while (isActive) {
            runCatching { servico.purgar() }
                .onFailure { log.warn("Purga de notificações falhou; tenta no próximo ciclo", it) }
            delay(1.days)
        }
    }
}

private fun Application.configureSerialization() {
    install(ContentNegotiation) { json() }
}

private fun Application.configureMonitoring() {
    install(DefaultHeaders)
    install(CallLogging) {
        level = Level.INFO
        filter { it.request.local.uri != "/health" }   // não polui o log com o ping de health
    }
}

// Skeleton: catch-all genérico. No 1.2 isto vira o mapeamento AppError -> HTTP
// com o envelope de erro vindo de shared-contract.
private fun Application.configureStatusPages() {
    install(StatusPages) {
        // Exceção não prevista -> 500 genérico, stacktrace só no log.
        exception<Throwable> { call, cause ->
            call.application.log.error("Erro não tratado em ${call.request.local.uri}", cause)
            val (status, body) = AppError.Unexpected(cause = cause).toHttp()
            call.respond(status, body)
        }
        // 404 de rota não casada -> mesmo envelope, p/ consistência da API.
        status(HttpStatusCode.NotFound) { call, _ ->
            call.respond(
                HttpStatusCode.NotFound,
                ErrorResponse(ErrorCodes.NOT_FOUND, "Recurso não encontrado"),
            )
        }
    }
}