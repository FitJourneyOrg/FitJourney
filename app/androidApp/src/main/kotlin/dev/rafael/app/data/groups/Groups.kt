package dev.rafael.app.data.groups

import dev.rafael.contract.group.CreateGroupRequest
import dev.rafael.contract.group.GroupDto
import dev.rafael.contract.group.GroupInviteDto
import dev.rafael.contract.group.GroupPreviewDto
import dev.rafael.core.result.AppResult
import kotlinx.coroutines.flow.Flow

/**
 * Grupos como as TELAS os enxergam (ARCH #33, fatia A.3).
 *
 * **Leitura é cache-first** ([REGRA] #30): a aba Grupos abre com a última lista conhecida, com
 * ou sem rede. **Escrita é online-only**, e aqui o motivo é mais forte que no nome do usuário
 * (#34): o código do grupo é gerado pelo SERVIDOR, e o estado depende do relógio do servidor.
 * Um grupo otimista local seria um grupo sem código e sem estado — nada que se possa mostrar.
 */
interface Groups {

    /** Meus grupos, do cache local. Nunca falha; lista vazia antes do primeiro sync da vida. */
    fun observar(): Flow<List<GroupDto>>

    /** Busca no servidor e grava no cache; o Flow re-emite. Offline: não faz nada, sem erro. */
    suspend fun sincronizar(forcar: Boolean = false)

    /** Já sincronizou alguma vez nesta conta, neste aparelho? Distingue "não baixei" de "não tenho". */
    suspend fun jaSincronizou(): Boolean

    suspend fun criar(req: CreateGroupRequest): AppResult<GroupDto>

    /** O que se vê antes de entrar. `code` OU `inviteToken` — nunca os dois. */
    suspend fun preview(code: String? = null, inviteToken: String? = null): AppResult<GroupPreviewDto>

    suspend fun entrarPorCodigo(code: String): AppResult<GroupDto>
    suspend fun entrarPorConvite(token: String): AppResult<GroupDto>

    suspend fun sair(groupId: String): AppResult<Unit>
    suspend fun expulsar(groupId: String, userId: String): AppResult<Unit>
    suspend fun transferirAdmin(groupId: String, userId: String): AppResult<Unit>

    suspend fun gerarConvite(groupId: String): AppResult<GroupInviteDto>
    suspend fun revogarConvite(groupId: String): AppResult<Unit>
}
