package dev.rafael.app.data.stats

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToOneOrNull
import dev.rafael.contract.stats.UserStatsDto
import dev.rafael.core.database.FitJourneyDatabase
import dev.rafael.core.database.SyncStamps
import dev.rafael.core.network.TokenProvider
import dev.rafael.core.result.AppResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/**
 * XP/nível/streak — OFFLINE-FIRST na leitura.
 *
 * A tela observa o CACHE LOCAL, então a faixa de progresso aparece sempre: com rede, sem rede,
 * no primeiro frame. O `sincronizar()` busca no servidor e grava; o Flow re-emite e a tela
 * se atualiza sozinha.
 *
 * [REGRA] O cálculo continua sendo do SERVIDOR (ARCH #16) — o cliente não inventa XP. Por isso
 * o XP de um treino feito offline só entra depois que a sessão sobe e o servidor recalcula:
 * o número mostrado é sempre a última verdade conhecida, nunca um palpite local.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StatsRepository(
    private val api: StatsApi,
    private val db: FitJourneyDatabase,
    private val tokenProvider: TokenProvider,
    private val stamps: SyncStamps,
) : Stats {
    private val cache = db.cacheQueries
    private val json = Json { ignoreUnknownKeys = true }

    /** Chave POR USUÁRIO: sem isso, trocar de conta mostraria o XP da conta anterior. */
    private suspend fun chave(): String = "stats:${tokenProvider.currentUid() ?: ""}"

    /** Último XP/nível/streak conhecido. Nunca falha; null só antes do primeiro sync da vida. */
    /** Re-chaveia quando a SESSÃO muda — ver `TokenProvider.uidFlow`. */
    override fun observar(): Flow<UserStatsDto?> =
        tokenProvider.uidFlow().flatMapLatest { uid ->
            cache.get("stats:${uid ?: ""}")
                .asFlow()
                .mapToOneOrNull(Dispatchers.Default)
                .map { payload ->
                    payload?.let {
                        runCatching { json.decodeFromString(UserStatsDto.serializer(), it) }.getOrNull()
                    }
                }
        }

    /** Busca no servidor e grava no cache (o Flow re-emite). Offline: não faz nada, sem erro. */
    /**
     * Busca no servidor e grava no cache (o Flow re-emite). Offline: não faz nada, sem erro.
     *
     * TTL: a Home chama isto em TODO `onResume`, então sem janela de frescor trocar de aba 6
     * vezes gerava 6 `GET /me/stats`. XP não muda sozinho — só quando uma sessão sobe, e nesse
     * caso quem chama passa `forcar = true`.
     *
     * @param forcar ignora o TTL. Use quando você SABE que o XP mudou (pendência sincronizada).
     */
    override suspend fun sincronizar(forcar: Boolean) {
        // O carimbo é chaveado por uid, então trocar de conta já não reaproveita nada —
        // não precisa mais comparar o dono na mão.
        if (!forcar && stamps.fresco(SyncStamps.STATS, TTL_MS)) return
        val k = chave()
        when (val r = api.get()) {
            is AppResult.Success -> withContext(Dispatchers.Default) {
                cache.put(k, json.encodeToString(UserStatsDto.serializer(), r.value))
            }.also { stamps.marcar(SyncStamps.STATS) }
            is AppResult.Failure -> Unit   // mantém o último valor conhecido
        }
    }

    private companion object {
        /** Menor que o dos programas (5 min): XP é o número que o usuário mais olha. */
        const val TTL_MS = 2 * 60 * 1000L
    }
}
