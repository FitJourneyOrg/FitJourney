package dev.rafael.app.data.session

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import dev.rafael.app.data.sync.SyncScheduler
import dev.rafael.contract.session.WorkoutSessionDto
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
import kotlin.time.Clock

/** Sessão como a UI a enxerga: o dado + se ainda está esperando o servidor. */
data class SessaoLocal(
    val dto: WorkoutSessionDto,
    val pendente: Boolean,
)

/**
 * Sessões de treino — OFFLINE-FIRST de verdade.
 *
 * O banco local é a fonte da verdade da tela: `observarHistorico()` emite o que está no
 * aparelho (sincronizado + pendente) e a UI nunca espera rede. O sync é encanamento: quando
 * o servidor confirma, o marcador `pendente` cai e o Flow re-emite sozinho.
 *
 * O POST é idempotente por id (gerado no cliente), então reenviar nunca duplica.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SessionSync(
    private val api: SessionApi,
    private val db: FitJourneyDatabase,
    private val scheduler: SyncScheduler,
    private val tokenProvider: TokenProvider,
    private val stamps: SyncStamps,
) {
    private val q = db.workoutSessionQueries
    private val json = Json { ignoreUnknownKeys = true }

    /** uid do dono. Vazio = sem sessão ativa: não lê nem grava nada de ninguém. */
    private suspend fun uid(): String = tokenProvider.currentUid() ?: ""

    /**
     * Histórico local DO USUÁRIO ATUAL. Nunca falha, nunca espera rede.
     * É um Flow que troca de dono: ao logar com outra conta, re-emite a lista dela.
     */
    fun observarHistorico(): Flow<List<SessaoLocal>> =
        flow { emit(uid()) }.flatMapLatest { dono ->
            q.observarHistorico(dono)
            .asFlow()
            .mapToList(Dispatchers.Default)
            .map { linhas ->
                linhas.mapNotNull { linha ->
                    val dto = runCatching {
                        json.decodeFromString(WorkoutSessionDto.serializer(), linha.payload)
                    }.getOrNull() ?: return@mapNotNull null
                    SessaoLocal(dto = dto, pendente = linha.pending == 1L)
                }
            }
        }

    /** Salva a sessão executada (aparece no histórico NA HORA, como pendente) e tenta enviar. */
    suspend fun record(dto: WorkoutSessionDto) {
        val dono = uid()
        withContext(Dispatchers.Default) {
            q.salvarLocal(
                id = dto.id,
                uid = dono,
                payload = json.encodeToString(WorkoutSessionDto.serializer(), dto),
                finishedAt = dto.finishedAt,
            )
        }
        // Treino novo: o histórico do servidor mudou. Isto é MUTAÇÃO, não aposta — invalida
        // o carimbo para o próximo sync ir à rede em vez de esperar o TTL vencer.
        stamps.invalidar(SyncStamps.HISTORICO)
        flush()
    }

    /** Envia as pendentes. Confirmada = marcador cai (a linha CONTINUA no histórico). */
    suspend fun flush() {
        val dono = uid()
        if (dono.isEmpty()) return   // sem usuário logado não se envia nada
        val payloads = withContext(Dispatchers.Default) { q.pendentes(dono).executeAsList() }
        payloads.forEach { payload ->
            val dto = runCatching { json.decodeFromString(WorkoutSessionDto.serializer(), payload) }
                .getOrNull() ?: return@forEach
            if (api.post(dto) is AppResult.Success) {
                withContext(Dispatchers.Default) { q.marcarSincronizada(dto.id) }
            }
            // falha → segue pendente para a próxima tentativa
        }
        // sobrou algo? o WorkManager reenvia quando a rede voltar, mesmo com o app fechado.
        val aindaPendentes = withContext(Dispatchers.Default) { q.contarPendentes(dono).executeAsOne() }
        if (aindaPendentes > 0) scheduler.agendarAgora()
    }

    /**
     * Puxa o histórico do servidor para o banco local. Falha em silêncio: a tela já está
     * pintada com o local. NÃO mexe nas pendentes (elas ainda não existem lá).
     *
     * @param forcar ignora a janela de frescor (usar em pull-to-refresh / "tentar de novo").
     */
    suspend fun sincronizarHistorico(forcar: Boolean = false): AppResult<Unit> {
        val dono = uid()
        if (dono.isEmpty()) return AppResult.Success(Unit)

        // Carimbo PERSISTIDO (SyncStamps): quando esta janela vivia em memória, todo cold
        // start rebaixava o histórico inteiro — e o custo cresce com o tamanho dele.
        // O uid entra na chave, então trocar de conta não reaproveita nada.
        if (!forcar && stamps.fresco(SyncStamps.HISTORICO, TTL_HISTORICO_MS)) {
            return AppResult.Success(Unit)
        }
        val remoto = api.list()
        if (remoto is AppResult.Success) {
            withContext(Dispatchers.Default) {
                q.transaction {
                    remoto.value.forEach { dto ->
                        q.salvarDoServidor(
                            id = dto.id,
                            uid = dono,
                            payload = json.encodeToString(WorkoutSessionDto.serializer(), dto),
                            finishedAt = dto.finishedAt,
                        )
                    }
                }
            }
            stamps.marcar(SyncStamps.HISTORICO)
            return AppResult.Success(Unit)
        }
        return AppResult.Success(Unit)   // offline não é erro de tela
    }

    private companion object {
        /** Janela em que o histórico é considerado fresco (trocar de aba não rebaixa). */
        const val TTL_HISTORICO_MS = 5 * 60 * 1000L
    }
}
