package dev.rafael.core.database

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.time.Clock

/**
 * CARIMBOS DE SINCRONIZAÇÃO persistidos — quando cada coisa foi baixada com sucesso.
 *
 * O QUE resolve. Cada repositório tinha o seu par de campos em memória:
 * ```
 * private var sincronizadoEm: Long? = null
 * private var donoDoCache: String? = null
 * ```
 * escrito à mão em quatro lugares (programas, treinos, catálogo, stats). Isso trouxe dois
 * problemas:
 *
 * 1. **TTL em memória vale zero entre aberturas.** O catálogo de exercícios tem TTL de 24h
 *    porque só muda em deploy — e expirava a cada vez que o app fechava. Todo cold start
 *    rebaixava 965 exercícios.
 * 2. **`donoDoCache` replicado é onde bug de vazamento nasce.** Foi exatamente assim que dado
 *    de uma conta apareceu em outra: um lugar checava o dono, outro esquecia.
 *
 * COMO. Grava o instante do último sync no `kv_cache`, numa chave que inclui o uid. Assim o
 * isolamento por conta é consequência da chave, não de um `if` que alguém precisa lembrar de
 * escrever: a conta B simplesmente não encontra o carimbo da conta A.
 *
 * PARA QUE, no FitJourney: sustenta o cache-first do ARCH #30 atravessando o fechamento do
 * app. Um usuário que abre o app offline, tendo sincronizado ontem, continua vendo seus
 * programas — e o app sabe a diferença entre "não baixei ainda" e "você não tem nada".
 *
 * [REGRA] `chave` identifica O QUE foi sincronizado, nunca o usuário. O uid é acrescentado
 * aqui. Dado global (catálogo de exercícios) usa [Escopo.GLOBAL] e fica fora do uid.
 */
class SyncStamps(
    db: FitJourneyDatabase,
    private val uidAtual: suspend () -> String?,
) {
    private val cache = db.cacheQueries

    /** O carimbo pertence a um usuário ou ao aparelho? */
    enum class Escopo {
        /** Por conta: programas, treinos, stats, histórico. */
        USUARIO,

        /** Do aparelho: catálogo de exercícios, igual para todo mundo. */
        GLOBAL,
    }

    private suspend fun chaveCompleta(chave: String, escopo: Escopo): String = when (escopo) {
        Escopo.GLOBAL -> "sync:$chave"
        Escopo.USUARIO -> "sync:$chave:${uidAtual() ?: ""}"
    }

    /**
     * Sincronizou há menos de [ttlMs]? Falso quando nunca sincronizou — que é diferente de
     * "sincronizou e não veio nada", distinção que a UI usa para não dizer "você não tem
     * programas" a quem só não baixou ainda.
     */
    suspend fun fresco(chave: String, ttlMs: Long, escopo: Escopo = Escopo.USUARIO): Boolean {
        val quando = lerCarimbo(chave, escopo) ?: return false
        return Clock.System.now().toEpochMilliseconds() - quando < ttlMs
    }

    /** Já sincronizou alguma vez neste aparelho, com esta conta? (ignora o TTL) */
    suspend fun jaSincronizou(chave: String, escopo: Escopo = Escopo.USUARIO): Boolean =
        lerCarimbo(chave, escopo) != null

    /** Chame após um sync bem-sucedido. */
    suspend fun marcar(chave: String, escopo: Escopo = Escopo.USUARIO) {
        val k = chaveCompleta(chave, escopo)
        val agora = Clock.System.now().toEpochMilliseconds()
        withContext(Dispatchers.Default) { cache.put(k, agora.toString()) }
    }

    /**
     * Apaga o carimbo: o próximo `fresco()` devolve falso e o repositório vai à rede.
     * Use depois de MUTAÇÃO — aí não é aposta, você sabe que mudou.
     */
    suspend fun invalidar(chave: String, escopo: Escopo = Escopo.USUARIO) {
        val k = chaveCompleta(chave, escopo)
        withContext(Dispatchers.Default) { cache.deleteKey(k) }
    }

    private suspend fun lerCarimbo(chave: String, escopo: Escopo): Long? {
        val k = chaveCompleta(chave, escopo)
        return withContext(Dispatchers.Default) {
            cache.get(k).executeAsOneOrNull()?.toLongOrNull()
        }
    }

    companion object {
        // Nomes do que é sincronizado. Constantes para não haver typo silencioso entre o
        // lugar que marca e o que lê — um typo aqui vira "sempre vai à rede", sem erro visível.
        const val PROGRAMAS = "programs"
        const val CATALOGO = "exercises"
        const val STATS = "stats"

        /** `/me` — nome e plano (V35, ARCH #33). */
        const val ME = "me"

        /** Mesma janela do STATS: as duas telas mostram o mesmo progresso (ARCH #16). */
        const val CONQUISTAS = "achievements"
        const val HISTORICO = "sessions"

        /** Carimbo de um treino específico. */
        fun treino(id: String) = "workout:$id"
    }
}
