package dev.rafael.app.data.me

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToOneOrNull
import dev.rafael.contract.user.UserDto
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
import kotlinx.serialization.json.Json

/**
 * `/me` OFFLINE-FIRST na leitura (ARCH #30), espelhando o `StatsRepository`.
 *
 * Importa mais aqui do que no XP: o nome aparece no cabeçalho do menu lateral, que abre em
 * qualquer tela e a qualquer momento. Um cabeçalho que fica vazio no avião seria o tipo de
 * defeito que faz o app inteiro parecer quebrado.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MeRepository(
    private val api: MeApi,
    private val db: FitJourneyDatabase,
    private val tokenProvider: TokenProvider,
    private val stamps: SyncStamps,
) : Me {
    private val cache = db.cacheQueries
    private val json = Json { ignoreUnknownKeys = true }

    /** Chave POR USUÁRIO: sem isso, trocar de conta mostraria o nome da conta anterior. */
    private suspend fun chave(): String = "me:${tokenProvider.currentUid() ?: ""}"

    /**
     * Re-chaveia quando a SESSÃO muda, e não uma vez no início da coleta.
     *
     * A versão anterior resolvia a chave uma só vez; quem coletasse antes do login ficava preso
     * ao uid nulo para sempre — foi o cabeçalho do menu eternamente em "?".
     */
    override fun observar(): Flow<UserDto?> =
        tokenProvider.uidFlow().flatMapLatest { uid ->
            cache.get("me:${uid ?: ""}")
                .asFlow()
                .mapToOneOrNull(Dispatchers.Default)
                .map { payload ->
                    payload?.let {
                        runCatching { json.decodeFromString(UserDto.serializer(), it) }.getOrNull()
                    }
                }
        }

    override suspend fun sincronizar(forcar: Boolean) {
        // SEM SESSÃO não se sincroniza. Sem esta guarda, qualquer tela viva depois do logout
        // dispara um GET que volta 401 — e 401 acorda o SessionExpiryBus, que força navegação
        // para o Login. Foi assim que "sair da conta" virou uma tela travada e um pulo tardio
        // para o Login quando o menu reabria.
        if (tokenProvider.currentUid() == null) return
        if (!forcar && stamps.fresco(SyncStamps.ME, TTL_MS)) return
        when (val r = api.get()) {
            is AppResult.Success -> {
                gravar(r.value)
                stamps.marcar(SyncStamps.ME)
            }
            is AppResult.Failure -> Unit   // mantém o último nome conhecido
        }
    }

    /**
     * O servidor devolve o `UserDto` já normalizado — é ELE que vai pro cache, não o texto que
     * o usuário digitou. Gravar o digitado faria a tela mostrar "Rafael  Souza" com dois
     * espaços até o próximo sync, e aí o nome mudaria sozinho na cara do usuário.
     */
    override suspend fun renomear(nome: String): AppResult<String> =
        api.renomear(nome).map { atualizado ->
            gravar(atualizado)
            stamps.marcar(SyncStamps.ME)
            atualizado.displayName
        }

    private suspend fun gravar(dto: UserDto) {
        val k = chave()
        withContext(Dispatchers.Default) {
            cache.put(k, json.encodeToString(UserDto.serializer(), dto))
        }
    }

    private companion object {
        /**
         * 5 min. Maior que o do XP (2 min) porque nome e plano quase não mudam — e quando
         * mudam, mudam POR AQUI, e o `renomear` já grava o valor novo direto.
         */
        const val TTL_MS = 5 * 60 * 1000L
    }
}
