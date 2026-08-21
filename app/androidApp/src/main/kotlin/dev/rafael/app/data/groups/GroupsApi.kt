package dev.rafael.app.data.groups

import dev.rafael.contract.group.CreateGroupRequest
import dev.rafael.contract.group.GroupDto
import dev.rafael.contract.group.GroupInviteDto
import dev.rafael.contract.group.GroupPreviewDto
import dev.rafael.contract.group.JoinByCodeRequest
import dev.rafael.core.network.HttpClientFactory
import dev.rafael.core.network.httpResult
import dev.rafael.core.result.AppResult
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType

/** As rotas de grupo sobre `AppResult`. Mesmo desenho do `StatsApi`: sem lógica, só tradução. */
class GroupsApi(private val client: HttpClient) {

    private val base = HttpClientFactory.BASE_URL

    suspend fun listar(): AppResult<List<GroupDto>> =
        httpResult { client.get("$base/groups").body() }

    suspend fun criar(req: CreateGroupRequest): AppResult<GroupDto> =
        httpResult {
            client.post("$base/groups") {
                contentType(ContentType.Application.Json)
                setBody(req)
            }.body()
        }

    suspend fun preview(code: String?, inviteToken: String?): AppResult<GroupPreviewDto> =
        httpResult {
            client.get("$base/groups/preview") {
                code?.let { parameter("code", it) }
                inviteToken?.let { parameter("invite", it) }
            }.body()
        }

    suspend fun entrarPorCodigo(code: String): AppResult<GroupDto> =
        httpResult {
            client.post("$base/groups/join") {
                contentType(ContentType.Application.Json)
                setBody(JoinByCodeRequest(code))
            }.body()
        }

    suspend fun entrarPorConvite(token: String): AppResult<GroupDto> =
        httpResult { client.post("$base/invites/$token/join").body() }

    suspend fun sair(groupId: String): AppResult<Unit> =
        httpResult { client.post("$base/groups/$groupId/leave").body() }

    suspend fun expulsar(groupId: String, userId: String): AppResult<Unit> =
        httpResult { client.delete("$base/groups/$groupId/members/$userId").body() }

    suspend fun transferirAdmin(groupId: String, userId: String): AppResult<Unit> =
        httpResult { client.post("$base/groups/$groupId/admin/$userId").body() }

    suspend fun gerarConvite(groupId: String): AppResult<GroupInviteDto> =
        httpResult { client.post("$base/groups/$groupId/invite").body() }

    suspend fun revogarConvite(groupId: String): AppResult<Unit> =
        httpResult { client.delete("$base/groups/$groupId/invite").body() }
}
