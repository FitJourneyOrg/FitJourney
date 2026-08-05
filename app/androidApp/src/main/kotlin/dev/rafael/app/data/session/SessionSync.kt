package dev.rafael.app.data.session

import dev.rafael.contract.session.WorkoutSessionDto
import dev.rafael.core.database.FitJourneyDatabase
import dev.rafael.core.result.AppResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/**
 * Sync offline-first das sessões (Fase 5). record() enfileira na outbox local (sempre) e tenta
 * mandar; se falhar (offline), fica pendente e o flush() reenvia depois. O POST é idempotente
 * por id no server, então reenvio nunca duplica. "Salvo" pro usuário = está na outbox.
 */
class SessionSync(
    private val api: SessionApi,
    private val db: FitJourneyDatabase,
) {
    private val outbox = db.sessionQueries
    private val json = Json { ignoreUnknownKeys = true }

    /** Salva a sessão localmente e tenta sincronizar na hora. */
    suspend fun record(dto: WorkoutSessionDto) {
        withContext(Dispatchers.Default) {
            outbox.enqueue(dto.id, json.encodeToString(WorkoutSessionDto.serializer(), dto))
        }
        flush()
    }

    /** Reenvia todas as pendentes; remove só as que o server confirmou. */
    suspend fun flush() {
        val payloads = withContext(Dispatchers.Default) { outbox.pending().executeAsList() }
        payloads.forEach { payload ->
            val dto = runCatching { json.decodeFromString(WorkoutSessionDto.serializer(), payload) }.getOrNull()
                ?: return@forEach
            if (api.post(dto) is AppResult.Success) {
                withContext(Dispatchers.Default) { outbox.remove(dto.id) }
            }
            // falha → mantém na outbox p/ o próximo flush
        }
    }

    suspend fun history(): AppResult<List<WorkoutSessionDto>> = api.list()
}
