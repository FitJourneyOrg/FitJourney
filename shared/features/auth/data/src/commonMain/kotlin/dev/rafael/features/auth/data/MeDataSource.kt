package dev.rafael.features.auth.data

import dev.rafael.contract.user.UpdateMeRequest
import dev.rafael.contract.user.UserDto
import dev.rafael.core.network.HttpClientFactory
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType

class MeDataSource(private val client: HttpClient) {
    suspend fun getMe(): UserDto =
        client.get("${HttpClientFactory.BASE_URL}/me").body()

    /** POST /me/subscribe — ativa o premium (compra simulada na Fase 7 dev). */
    suspend fun subscribe(): UserDto =
        client.post("${HttpClientFactory.BASE_URL}/me/subscribe").body()

    /**
     * PATCH /me — renomeia (V35, ARCH #33/#34).
     *
     * Devolve o `UserDto` INTEIRO, não só o nome: quem chama guarda o `/me` em cache e pode
     * substituir a cópia local com a própria resposta, sem um `GET` extra.
     */
    suspend fun updateDisplayName(displayName: String): UserDto =
        client.patch("${HttpClientFactory.BASE_URL}/me") {
            contentType(ContentType.Application.Json)
            setBody(UpdateMeRequest(displayName))
        }.body()

    /**
     * PATCH /me — grava o idioma da CONTA (V47, fatia G.4).
     *
     * Só o `locale` viaja: o `displayName` fica `null`, que no [UpdateMeRequest] significa "não
     * mexer". Mandar o nome junto faria esta chamada reescrever um campo que ninguém pediu para
     * mudar — e um PATCH que toca o que não foi pedido é um PUT com nome errado.
     *
     * ⚠️ Tag não suportada é **recusa**, não fallback: quem pede `es` e recebe `200 OK` acreditaria
     * ter escolhido espanhol. Quem valida é o servidor, com `IdiomaPolicy.valida`.
     */
    suspend fun updateLocale(tag: String): UserDto =
        client.patch("${HttpClientFactory.BASE_URL}/me") {
            contentType(ContentType.Application.Json)
            setBody(UpdateMeRequest(locale = tag))
        }.body()
}