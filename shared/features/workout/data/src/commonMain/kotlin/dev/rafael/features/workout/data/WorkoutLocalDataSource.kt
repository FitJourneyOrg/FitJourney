package dev.rafael.features.workout.data

import dev.rafael.contract.workout.WorkoutDto
import dev.rafael.core.database.FitJourneyDatabase
import dev.rafael.core.network.TokenProvider
import kotlinx.serialization.json.Json

/**
 * Cache do detalhe de um treino (leitura offline). Chave "workout:{id}:{uid}" no kv_cache.
 *
 * [REGRA] A chave inclui o UID — o mesmo aparelho pode ter várias contas, e ids de treino
 * são únicos por usuário. Sem isso, o cache vazaria entre contas.
 */
class WorkoutLocalDataSource(
    db: FitJourneyDatabase,
    private val tokenProvider: TokenProvider,
) {
    private val cache = db.cacheQueries
    private val json = Json { ignoreUnknownKeys = true }

    private suspend fun key(id: String) = "workout:$id:${tokenProvider.currentUid() ?: ""}"

    suspend fun save(id: String, dto: WorkoutDto) {
        cache.put(key(id), json.encodeToString(WorkoutDto.serializer(), dto))
    }

    suspend fun read(id: String): WorkoutDto? {
        val payload = cache.get(key(id)).executeAsOneOrNull() ?: return null
        return runCatching { json.decodeFromString(WorkoutDto.serializer(), payload) }.getOrNull()
    }
}
