package dev.rafael.app.data.checkin

import dev.rafael.contract.checkin.CheckInDto
import dev.rafael.contract.checkin.CommentDto
import dev.rafael.contract.checkin.DenunciarRequest
import dev.rafael.contract.checkin.JulgarRequest
import dev.rafael.contract.checkin.ModerationBadgeDto
import dev.rafael.contract.checkin.NovoComentarioRequest
import dev.rafael.contract.checkin.ReagirRequest
import dev.rafael.contract.checkin.ReportItemDto
import dev.rafael.contract.group.RankingEntryDto
import dev.rafael.core.network.HttpClientFactory
import dev.rafael.core.network.httpResult
import dev.rafael.core.result.AppResult
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType

/** As rotas de check-in sobre `AppResult`. Sem lógica: só tradução. */
class CheckInsApi(private val client: HttpClient) {

    private val base = HttpClientFactory.BASE_URL

    /**
     * Multipart, e não JSON com base64.
     *
     * Base64 infla o corpo em 33% e obriga as duas pontas a segurar a imagem inteira como texto.
     * Multipart é o que o servidor já sabe limitar e o que deixa a foto ser uma parte com o
     * próprio `Content-Type`.
     */
    suspend fun criar(
        groupId: String,
        foto: ByteArray?,
        nomeDoLocal: String?,
        latitude: Double?,
        longitude: Double?,
    ): AppResult<CheckInDto> = httpResult {
        client.post("$base/groups/$groupId/checkins") {
            setBody(
                MultiPartFormDataContent(
                    formData {
                        foto?.let {
                            append(
                                "foto", it,
                                Headers.build {
                                    append(HttpHeaders.ContentType, ContentType.Image.JPEG.toString())
                                    // O nome do arquivo é irrelevante para o servidor (ele gera a
                                    // própria referência), mas sem `filename` a parte chega como
                                    // campo de formulário e não como arquivo.
                                    append(HttpHeaders.ContentDisposition, "filename=\"checkin.jpg\"")
                                },
                            )
                        }
                        nomeDoLocal?.let { append("nomeDoLocal", it) }
                        latitude?.let { append("latitude", it.toString()) }
                        longitude?.let { append("longitude", it.toString()) }
                    },
                ),
            )
        }.body()
    }

    /** O feed. `antesDe` é cursor ISO, não número de página — ver o repositório do servidor. */
    suspend fun feed(groupId: String, antesDe: String? = null, limite: Int? = null): AppResult<List<CheckInDto>> =
        httpResult {
            client.get("$base/groups/$groupId/checkins") {
                antesDe?.let { parameter("antesDe", it) }
                limite?.let { parameter("limite", it) }
            }.body()
        }

    suspend fun apagar(groupId: String, checkInId: String): AppResult<Unit> =
        httpResult { client.delete("$base/groups/$groupId/checkins/$checkInId").body() }

    /** O ranking (7.2). Sem paginação: o teto de 50 membros (2.2) é o teto da lista. */
    suspend fun ranking(groupId: String): AppResult<List<RankingEntryDto>> =
        httpResult { client.get("$base/groups/$groupId/ranking").body() }

    // ---- social (fatia E.1) ----

    suspend fun comentarios(groupId: String, checkInId: String): AppResult<List<CommentDto>> =
        httpResult { client.get("$base/groups/$groupId/checkins/$checkInId/comments").body() }

    suspend fun comentar(groupId: String, checkInId: String, texto: String): AppResult<CommentDto> =
        httpResult {
            client.post("$base/groups/$groupId/checkins/$checkInId/comments") {
                contentType(ContentType.Application.Json)
                setBody(NovoComentarioRequest(texto))
            }.body()
        }

    /** O comentário é identificado direto, sem o check-in: o id já é único no grupo. */
    suspend fun apagarComentario(groupId: String, comentarioId: String): AppResult<Unit> =
        httpResult { client.delete("$base/groups/$groupId/comments/$comentarioId").body() }

    suspend fun reagir(groupId: String, checkInId: String, emoji: String): AppResult<Unit> =
        httpResult {
            client.post("$base/groups/$groupId/checkins/$checkInId/reactions") {
                contentType(ContentType.Application.Json)
                setBody(ReagirRequest(emoji))
            }.body()
        }

    /** Sem corpo: só existe UMA reação minha, então não há o que identificar. */
    suspend fun desreagir(groupId: String, checkInId: String): AppResult<Unit> =
        httpResult { client.delete("$base/groups/$groupId/checkins/$checkInId/reactions").body() }

    // ---- moderação (fatia E.2) ----

    suspend fun denunciarCheckIn(groupId: String, checkInId: String, motivo: String): AppResult<Unit> =
        httpResult {
            client.post("$base/groups/$groupId/checkins/$checkInId/reports") {
                contentType(ContentType.Application.Json)
                setBody(DenunciarRequest(motivo))
            }.body()
        }

    suspend fun denunciarComentario(groupId: String, comentarioId: String, motivo: String): AppResult<Unit> =
        httpResult {
            client.post("$base/groups/$groupId/comments/$comentarioId/reports") {
                contentType(ContentType.Application.Json)
                setBody(DenunciarRequest(motivo))
            }.body()
        }

    /** A fila do admin. Membro comum leva 403 — a tela nem chega a pedir. */
    suspend fun fila(groupId: String): AppResult<List<ReportItemDto>> =
        httpResult { client.get("$base/groups/$groupId/reports").body() }

    /** Só o número, para o badge. Rota separada da fila — ver o KDoc da rota no servidor. */
    suspend fun pendentes(groupId: String): AppResult<ModerationBadgeDto> =
        httpResult { client.get("$base/groups/$groupId/reports/count").body() }

    /**
     * O julgamento. O id é o do **ALVO**, não o da denúncia — é o que faz várias denúncias do
     * mesmo conteúdo fecharem juntas (6.11).
     */
    suspend fun julgar(groupId: String, alvoId: String, acatar: Boolean): AppResult<Unit> =
        httpResult {
            client.post("$base/groups/$groupId/reports/$alvoId/judgment") {
                contentType(ContentType.Application.Json)
                setBody(JulgarRequest(acatar))
            }.body()
        }

    /** 6.10: invalidar sem denúncia prévia. */
    suspend fun invalidar(groupId: String, checkInId: String): AppResult<Unit> =
        httpResult { client.post("$base/groups/$groupId/checkins/$checkInId/invalidate").body() }
}
