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
 * pela implementação. Dado global (catálogo de exercícios) usa [Escopo.GLOBAL] e fica fora do uid.
 *
 * ## Por que é interface (B2 de 2026-10-07)
 *
 * Era classe concreta e exigia [FitJourneyDatabase]. Todo repositório do cliente que a recebe
 * ficava impossível de construir num teste de unidade, e o offline-first só podia ser provado no
 * ViewModel contra um repositório falso (ver a decisão (a) do plano da Fase 8). Agora o
 * repositório recebe esta interface, e o teste passa um fake escrito à mão.
 *
 * A implementação com SQLDelight é [SyncStampsImpl]. O [Escopo] e as chaves ficam AQUI, na
 * interface, para quem usa `SyncStamps.PROGRAMAS` ou `SyncStamps.Escopo.GLOBAL` não mudar.
 */
interface SyncStamps {

    /** O carimbo pertence a um usuário ou ao aparelho? */
    enum class Escopo {
        /** Por conta: programas, treinos, stats, histórico. */
        USUARIO,

        /** Do aparelho: catálogo de exercícios, igual para todo mundo. */
        GLOBAL,
    }

    /**
     * Sincronizou há menos de [ttlMs]? Falso quando nunca sincronizou — que é diferente de
     * "sincronizou e não veio nada", distinção que a UI usa para não dizer "você não tem
     * programas" a quem só não baixou ainda.
     */
    suspend fun fresco(chave: String, ttlMs: Long, escopo: Escopo = Escopo.USUARIO): Boolean

    /** Já sincronizou alguma vez neste aparelho, com esta conta? (ignora o TTL) */
    suspend fun jaSincronizou(chave: String, escopo: Escopo = Escopo.USUARIO): Boolean

    /** Chame após um sync bem-sucedido. */
    suspend fun marcar(chave: String, escopo: Escopo = Escopo.USUARIO)

    /**
     * Apaga o carimbo: o próximo `fresco()` devolve falso e o repositório vai à rede.
     * Use depois de MUTAÇÃO — aí não é aposta, você sabe que mudou.
     */
    suspend fun invalidar(chave: String, escopo: Escopo = Escopo.USUARIO)

    companion object {
        // Nomes do que é sincronizado. Constantes para não haver typo silencioso entre o
        // lugar que marca e o que lê — um typo aqui vira "sempre vai à rede", sem erro visível.
        const val PROGRAMAS = "programs"
        const val CATALOGO = "exercises"

        /**
         * Acervo do "Aprender" (Fase 8). GLOBAL como o catálogo, e pela mesma razão: o conteúdo
         * é do aparelho, não da conta. A chave real leva o idioma como sufixo -- ver
         * `WikiRepositoryImpl.refresh`.
         */
        const val WIKI = "wiki"
        const val STATS = "stats"

        /** `/me` — nome e plano (V35, ARCH #33). */
        const val ME = "me"

        /** Meus grupos (Fase 6, ARCH #33). */
        const val GRUPOS = "groups"

        /** Mesma janela do STATS: as duas telas mostram o mesmo progresso (ARCH #16). */
        const val CONQUISTAS = "achievements"
        const val HISTORICO = "sessions"

        /**
         * Analise de progressao (J.2). Mesma janela do [STATS], e pelo mesmo motivo: os dois
         * numeros saem das MESMAS sessoes, entao mudam juntos. Carimbos diferentes fariam a
         * tonelagem do topo e o grafico de baixo discordarem por ate dois minutos.
         */
        const val PROGRESSO = "progress"

        /** Carimbo de um treino específico. */
        fun treino(id: String) = "workout:$id"
    }
}

/**
 * A implementação de produção: grava o instante no `kv_cache` do SQLDelight, com o uid na chave.
 * O motivo de o uid vir como lambda está no `AppModule` (`core:database` não pode depender de
 * `core:network`).
 */
class SyncStampsImpl(
    db: FitJourneyDatabase,
    private val uidAtual: suspend () -> String?,
) : SyncStamps {
    private val cache = db.cacheQueries

    private suspend fun chaveCompleta(chave: String, escopo: SyncStamps.Escopo): String = when (escopo) {
        SyncStamps.Escopo.GLOBAL -> "sync:$chave"
        SyncStamps.Escopo.USUARIO -> "sync:$chave:${uidAtual() ?: ""}"
    }

    override suspend fun fresco(chave: String, ttlMs: Long, escopo: SyncStamps.Escopo): Boolean {
        val quando = lerCarimbo(chave, escopo) ?: return false
        return Clock.System.now().toEpochMilliseconds() - quando < ttlMs
    }

    override suspend fun jaSincronizou(chave: String, escopo: SyncStamps.Escopo): Boolean =
        lerCarimbo(chave, escopo) != null

    override suspend fun marcar(chave: String, escopo: SyncStamps.Escopo) {
        val k = chaveCompleta(chave, escopo)
        val agora = Clock.System.now().toEpochMilliseconds()
        withContext(Dispatchers.Default) { cache.put(k, agora.toString()) }
    }

    override suspend fun invalidar(chave: String, escopo: SyncStamps.Escopo) {
        val k = chaveCompleta(chave, escopo)
        withContext(Dispatchers.Default) { cache.deleteKey(k) }
    }

    private suspend fun lerCarimbo(chave: String, escopo: SyncStamps.Escopo): Long? {
        val k = chaveCompleta(chave, escopo)
        return withContext(Dispatchers.Default) {
            cache.get(k).executeAsOneOrNull()?.toLongOrNull()
        }
    }
}
