package dev.rafael.app.data.achievements

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToOneOrNull
import dev.rafael.contract.stats.AchievementDto
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
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * Conquistas — OFFLINE-FIRST na leitura (ARCH #30), igual ao StatsRepository.
 *
 * A tela observa o CACHE LOCAL, então a grade de medalhas aparece no primeiro frame, com ou
 * sem rede. O `sincronizar()` busca, grava, e o Flow re-emite.
 *
 * Guardado como blob no `kv_cache` e não em tabela: o catálogo é sempre lido INTEIRO e nunca
 * consultado por dentro (não existe "quais conquistas de streak eu tenho?"). Normalizar seria
 * uma tabela sem uma única query que a justifique.
 *
 * [REGRA] Chaveado por uid — trocar de conta não pode mostrar a medalha da conta anterior.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AchievementsRepository(
    private val api: AchievementsApi,
    private val db: FitJourneyDatabase,
    private val tokenProvider: TokenProvider,
    private val stamps: SyncStamps,
) : Achievements {
    private val cache = db.cacheQueries
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(AchievementDto.serializer())

    private suspend fun chave(): String = "achievements:${tokenProvider.currentUid() ?: ""}"

    /** Re-chaveia quando a SESSÃO muda — ver `TokenProvider.uidFlow`. */
    override fun observar(): Flow<List<AchievementDto>> =
        tokenProvider.uidFlow().flatMapLatest { uid ->
            cache.get("achievements:${uid ?: ""}")
                .asFlow()
                .mapToOneOrNull(Dispatchers.Default)
                .map { payload ->
                    // Lista vazia, não null: "ainda não baixei" e "não tenho nada" se resolvem
                    // do mesmo jeito na tela (grade toda em cinza). Foi confundir esses dois
                    // que quebrou o cache-first dos programas.
                    payload?.let {
                        runCatching { json.decodeFromString(serializer, it) }.getOrNull()
                    }.orEmpty()
                }
        }

    /**
     * TTL igual ao do XP (2 min) de propósito: as duas telas mostram o mesmo progresso por
     * ângulos diferentes, e janelas distintas fariam a medalha aparecer minutos depois do nível
     * que a desbloqueou — o usuário leria como bug.
     */
    override suspend fun sincronizar(forcar: Boolean) {
        if (tokenProvider.currentUid() == null) return   // sem sessão, sincronizar só produz 401
        if (!forcar && stamps.fresco(SyncStamps.CONQUISTAS, TTL_MS)) return
        val k = chave()
        when (val r = api.get()) {
            is AppResult.Success -> withContext(Dispatchers.Default) {
                cache.put(k, json.encodeToString(serializer, r.value))
            }.also { stamps.marcar(SyncStamps.CONQUISTAS) }
            is AppResult.Failure -> Unit   // mantém o último catálogo conhecido
        }
    }

    private companion object {
        const val TTL_MS = 2 * 60 * 1000L
    }
}
