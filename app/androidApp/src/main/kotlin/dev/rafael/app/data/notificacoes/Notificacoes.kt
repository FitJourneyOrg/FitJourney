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
 * Online-only, como o resto da Fase 6 — mas aqui há um motivo a mais e ele é decisivo: **a
 * notificação não-lida é um estado do SERVIDOR**, e o contador do ícone precisa refletir o que
 * está lá, não o que este aparelho viu por último. Alguém que marca tudo como lido no tablet
 * espera o celular concordar.
 */
interface Notificacoes {
    suspend fun listar(): AppResult<List<NotificacaoDto>>

    /** Chamada ao ABRIR a central: abrir é o gesto de "vi tudo isto". */
    suspend fun marcarComoLidas(): AppResult<Unit>

    suspend fun registrarDispositivo(token: String): AppResult<Unit>
    suspend fun darBaixaNoDispositivo(token: String): AppResult<Unit>
}

class NotificacoesApi(private val client: HttpClient) : Notificacoes {

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
