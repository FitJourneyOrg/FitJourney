package dev.rafael.app.data.groups

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToOneOrNull
import dev.rafael.contract.group.CreateGroupRequest
import dev.rafael.contract.group.GroupDto
import dev.rafael.contract.group.GroupInviteDto
import dev.rafael.contract.group.GroupPreviewDto
import dev.rafael.core.database.FitJourneyDatabase
import dev.rafael.core.database.SyncStamps
import dev.rafael.core.network.TokenProvider
import dev.rafael.core.result.AppResult
import dev.rafael.core.result.map
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * Meus grupos, OFFLINE-FIRST na leitura (ARCH #30), no mesmo desenho do `StatsRepository`.
 *
 * A lista vai para o `kv_cache` como blob, e não para tabelas próprias: diferente de programas
 * e treinos, nada aqui é editado localmente nem entra em outbox. O blob é a forma mais simples
 * que atende, e trocá-lo por tabelas depois é decisão desta classe, não das telas.
 *
 * **Toda mutação invalida o carimbo e ressincroniza.** Não é aposta: entrar, sair, expulsar e
 * transferir mudam a lista, e nós SABEMOS disso — é o critério do Painel ("`invalidate()` para
 * o que você controla; TTL para o que não controla").
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GroupsRepository(
    private val api: GroupsApi,
    private val db: FitJourneyDatabase,
    private val tokenProvider: TokenProvider,
    private val stamps: SyncStamps,
) : Groups {
    private val cache = db.cacheQueries
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(GroupDto.serializer())

    /** Chave POR USUÁRIO: sem isso, trocar de conta mostraria os grupos da conta anterior. */
    private suspend fun chave(): String = "groups:${tokenProvider.currentUid() ?: ""}"

    /** Re-chaveia quando a SESSÃO muda — ver `TokenProvider.uidFlow`. */
    override fun observar(): Flow<List<GroupDto>> =
        tokenProvider.uidFlow().flatMapLatest { uid ->
            cache.get("groups:${uid ?: ""}")
                .asFlow()
                .mapToOneOrNull(Dispatchers.Default)
                .map { payload ->
                    payload?.let {
                        runCatching { json.decodeFromString(serializer, it) }.getOrNull()
                    }.orEmpty()
                }
        }

    override suspend fun sincronizar(forcar: Boolean) {
        if (tokenProvider.currentUid() == null) return   // sem sessão, sincronizar só produz 401
        if (!forcar && stamps.fresco(SyncStamps.GRUPOS, TTL_MS)) return
        when (val r = api.listar()) {
            is AppResult.Success -> {
                gravar(r.value)
                stamps.marcar(SyncStamps.GRUPOS)
            }
            is AppResult.Failure -> Unit   // mantém a última lista conhecida
        }
    }

    override suspend fun jaSincronizou(): Boolean = stamps.jaSincronizou(SyncStamps.GRUPOS)

    override suspend fun criar(req: CreateGroupRequest): AppResult<GroupDto> =
        api.criar(req).map { it.also { aposMutacao() } }

    override suspend fun preview(code: String?, inviteToken: String?): AppResult<GroupPreviewDto> =
        api.preview(code, inviteToken)

    override suspend fun entrarPorCodigo(code: String): AppResult<GroupDto> =
        api.entrarPorCodigo(code).map { it.also { aposMutacao() } }

    override suspend fun entrarPorConvite(token: String): AppResult<GroupDto> =
        api.entrarPorConvite(token).map { it.also { aposMutacao() } }

    override suspend fun sair(groupId: String): AppResult<Unit> =
        api.sair(groupId).map { aposMutacao() }

    override suspend fun expulsar(groupId: String, userId: String): AppResult<Unit> =
        api.expulsar(groupId, userId).map { aposMutacao() }

    override suspend fun transferirAdmin(groupId: String, userId: String): AppResult<Unit> =
        api.transferirAdmin(groupId, userId).map { aposMutacao() }

    override suspend fun gerarConvite(groupId: String): AppResult<GroupInviteDto> =
        api.gerarConvite(groupId)

    override suspend fun revogarConvite(groupId: String): AppResult<Unit> =
        api.revogarConvite(groupId)

    /**
     * Ressincroniza FORÇANDO: a mutação acabou de mudar a lista, então esperar o TTL mostraria
     * dado velho logo depois de uma ação do próprio usuário — que é quando ele mais repara.
     */
    private suspend fun aposMutacao() {
        sincronizar(forcar = true)
    }

    private suspend fun gravar(grupos: List<GroupDto>) {
        val k = chave()
        withContext(Dispatchers.Default) {
            cache.put(k, json.encodeToString(serializer, grupos))
        }
    }

    private companion object {
        /**
         * 5 min — mas a TELA ignora este TTL ao entrar (ver `GruposViewModel.carregar`).
         *
         * Ele sobrou como rede de segurança para chamadas de fundo futuras (o `SyncWorker`, por
         * exemplo). Para a lista de grupos ele não serve como frescor: a contagem de membros
         * muda por ação de OUTRAS pessoas, e aí atrasar cinco minutos é atraso, não economia.
         */
        const val TTL_MS = 5 * 60 * 1000L
    }
}
