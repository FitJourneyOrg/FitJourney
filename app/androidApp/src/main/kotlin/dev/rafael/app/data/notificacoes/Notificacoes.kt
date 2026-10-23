package dev.rafael.app.data.notificacoes

import dev.rafael.contract.notificacao.NotificacaoDto
import dev.rafael.contract.notificacao.RegistrarDispositivoRequest
import dev.rafael.core.network.HttpClientFactory
import dev.rafael.core.network.httpResult
import dev.rafael.core.result.AppResult
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType

/**
 * A central de notificações e o registro de aparelho (F.1).
 *
 * CACHE-FIRST desde 2026-10-22 (fatia "notificações offline") -- este CONTRATO não mudou uma
 * letra, só a implementação por trás dele: era [NotificacoesApi] ligada direto na rede, agora é
 * [NotificacoesRepositoryImpl]. É por isso que [ContadorDeNaoLidas] e [NotificacoesViewModel]
 * não precisaram mudar: eles já pediam exatamente isto, e não precisam saber que existe um
 * banco local por trás.
 *
 * `listar()`: offline com sincronização anterior, devolve o último estado conhecido em vez de
 * falhar -- é o que resolve "modo avião some a lista". `marcarComoLidas()`: grava local na hora
 * e enfileira o envio -- é o que resolve "marcar como lida offline".
 */
interface Notificacoes {
    suspend fun listar(): AppResult<List<NotificacaoDto>>

    /** Chamada ao ABRIR a central: abrir é o gesto de "vi tudo isto". */
    suspend fun marcarComoLidas(): AppResult<Unit>

    suspend fun registrarDispositivo(token: String): AppResult<Unit>
    suspend fun darBaixaNoDispositivo(token: String): AppResult<Unit>
}

/**
 * Acesso cru à rede (ARCH #30: todo repositório cache-first separa REMOTO de LOCAL). Quem
 * monta a experiência cache-first é [NotificacoesRepositoryImpl], que usa isto por dentro.
 *
 * `marcarComoLidas()` é GLOBAL por desenho do servidor -- um POST sem id, "marque tudo". É por
 * isso que a marcação no outbox usa um alvo fixo por conta, não por notificação (ver
 * [NotificacoesRepositoryImpl.ALVO_MARCAR_LIDAS]).
 */
interface NotificacoesRemota {
    suspend fun listar(): AppResult<List<NotificacaoDto>>
    suspend fun marcarComoLidas(): AppResult<Unit>
    suspend fun registrarDispositivo(token: String): AppResult<Unit>
    suspend fun darBaixaNoDispositivo(token: String): AppResult<Unit>
}

class NotificacoesApi(private val client: HttpClient) : NotificacoesRemota {

    private val base = HttpClientFactory.BASE_URL

    override suspend fun listar(): AppResult<List<NotificacaoDto>> =
        httpResult { client.get("$base/me/notifications").body() }

    override suspend fun marcarComoLidas(): AppResult<Unit> =
        httpResult { client.post("$base/me/notifications/read").body() }

    override suspend fun registrarDispositivo(token: String): AppResult<Unit> =
        httpResult {
            client.post("$base/me/devices") {
                contentType(ContentType.Application.Json)
                setBody(RegistrarDispositivoRequest(token))
            }.body()
        }

    override suspend fun darBaixaNoDispositivo(token: String): AppResult<Unit> =
        httpResult { client.delete("$base/me/devices/$token").body() }
}
