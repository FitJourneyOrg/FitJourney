package dev.rafael.features.stats.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToOneOrNull
import dev.rafael.contract.i18n.Idioma
import dev.rafael.contract.stats.ProgressDto
import dev.rafael.core.database.FitJourneyDatabase
import dev.rafael.core.database.SyncStamps
import dev.rafael.core.network.TokenProvider
import dev.rafael.core.result.AppResult
import dev.rafael.features.stats.domain.Progresso
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/**
 * Analise de progressao — OFFLINE-FIRST na leitura, igual ao [StatsRepository].
 *
 * A tela observa o CACHE LOCAL, entao os graficos aparecem no primeiro frame e continuam
 * aparecendo sem rede. O `sincronizar()` busca e grava; o Flow re-emite.
 *
 * ## O cache guarda o nulo do portao, e isso e de proposito
 *
 * Para um usuario free, o DTO gravado tem os tres blocos nulos e `analysisLocked = true`. Quem
 * assina ve o paywall por mais alguns segundos — ate o sync da entrada da tela — e a tela se
 * corrige sozinha. E a mesma defasagem que o XP ja tem, e o preco de a tela funcionar offline.
 *
 * ## O idioma faz parte da CHAVE
 *
 * O DTO carrega nome de exercicio ja traduzido. Sem o idioma na chave, trocar o aparelho para
 * ingles mostraria o grafico com os nomes em portugues ate o TTL vencer — e o TTL e de dois
 * minutos, tempo de sobra para a pessoa achar que a traducao nao existe.
 *
 * > **Cache de conteudo traduzido que nao leva o idioma na chave e cache de um idioma so.**
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ProgressRepository(
    private val api: ProgressApi,
    private val db: FitJourneyDatabase,
    private val tokenProvider: TokenProvider,
    private val stamps: SyncStamps,
    private val idiomaAtual: () -> Idioma,
) : Progresso {
    private val cache = db.cacheQueries
    private val json = Json { ignoreUnknownKeys = true }

    private fun chave(uid: String?, idioma: Idioma) = "progress:${idioma.tag}:${uid ?: ""}"

    /** Re-chaveia quando a SESSAO muda — mesmo arranjo do [StatsRepository.observar]. */
    override fun observar(): Flow<ProgressDto?> =
        tokenProvider.uidFlow().flatMapLatest { uid ->
            cache.get(chave(uid, idiomaAtual()))
                .asFlow()
                .mapToOneOrNull(Dispatchers.Default)
                .map { payload ->
                    payload?.let {
                        runCatching { json.decodeFromString(ProgressDto.serializer(), it) }.getOrNull()
                    }
                }
        }

    override suspend fun sincronizar(forcar: Boolean) {
        if (tokenProvider.currentUid() == null) return   // sem sessao, so produziria 401
        if (!forcar && stamps.fresco(SyncStamps.PROGRESSO, TTL_MS)) return
        val idioma = idiomaAtual()
        val k = chave(tokenProvider.currentUid(), idioma)
        when (val r = api.get(idioma.tag)) {
            is AppResult.Success -> {
                withContext(Dispatchers.Default) {
                    cache.put(k, json.encodeToString(ProgressDto.serializer(), r.value))
                }
                stamps.marcar(SyncStamps.PROGRESSO)
            }
            // Mantem a ultima analise conhecida: erro de rede nao apaga o grafico de ontem.
            is AppResult.Failure -> Unit
        }
    }

    private companion object {
        /** A mesma do STATS: os dois numeros saem das mesmas sessoes e mudam juntos. */
        const val TTL_MS = 2 * 60 * 1000L
    }
}
