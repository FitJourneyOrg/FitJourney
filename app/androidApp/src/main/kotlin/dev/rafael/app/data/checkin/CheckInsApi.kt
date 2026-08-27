package dev.rafael.app.data.checkin

import dev.rafael.contract.checkin.CheckInDto
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
}
