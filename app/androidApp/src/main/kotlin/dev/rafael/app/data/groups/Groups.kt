package dev.rafael.app.data.groups

import dev.rafael.contract.group.CreateGroupRequest
import dev.rafael.contract.group.GroupDto
import dev.rafael.contract.group.GroupInviteDto
import dev.rafael.contract.group.GroupMemberDto
import dev.rafael.contract.group.GroupPreviewDto
import dev.rafael.core.result.AppError
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
    /**
     * Baixa a lista do servidor e grava no cache.
     *
     * ⚠️ **Devolve o erro, e isso é uma correção de 2026-09-11.** Antes devolvia `Unit`: a falha
     * era engolida com `is AppResult.Failure -> Unit  // mantém a última lista conhecida`. A
     * política de cache estava certa — não apagar o que já se sabe —, mas jogar fora a INFORMAÇÃO
     * de que falhou deixava a tela sem como distinguir "ainda não chegou" de "não vai chegar".
     *
     * Quando não havia lista nenhuma, "manter a última conhecida" significava manter nada, e a
     * tela ficava em *"Carregando seus desafios"* para sempre. O `GruposState` já declarava um
     * `erroSync` que **nunca teve como ser preenchido**, porque esta assinatura não entregava nada.
     *
     * > **Campo de erro que ninguém preenche é pior que nenhum: quem lê o estado conclui que o**
     * > **erro está tratado.**
     *
     * @return `null` quando deu certo — inclusive quando não precisou sincronizar (sem sessão, ou
     *   carimbo ainda fresco). O erro, quando a rede ou o servidor falharam.
     */
    suspend fun sincronizar(forcar: Boolean = false): AppError?

    /** Já sincronizou alguma vez nesta conta, neste aparelho? Distingue "não baixei" de "não tenho". */
    suspend fun jaSincronizou(): Boolean

    suspend fun criar(req: CreateGroupRequest): AppResult<GroupDto>

    /** O que se vê antes de entrar. `code` OU `inviteToken` — nunca os dois. */
    suspend fun preview(code: String? = null, inviteToken: String? = null): AppResult<GroupPreviewDto>

    suspend fun entrarPorCodigo(code: String): AppResult<GroupDto>
    suspend fun entrarPorConvite(token: String): AppResult<GroupDto>

    /** Um grupo específico, direto do servidor. A lista em cache não serve: ela não tem membros. */
    suspend fun porId(groupId: String): AppResult<GroupDto>

    /** Quem está no grupo. Nome e papel, nunca e-mail ([REGRA] #33). */
    suspend fun membros(groupId: String): AppResult<List<GroupMemberDto>>

    suspend fun sair(groupId: String): AppResult<Unit>
    suspend fun expulsar(groupId: String, userId: String): AppResult<Unit>
    suspend fun transferirAdmin(groupId: String, userId: String): AppResult<Unit>

    suspend fun gerarConvite(groupId: String): AppResult<GroupInviteDto>
    suspend fun revogarConvite(groupId: String): AppResult<Unit>
}
