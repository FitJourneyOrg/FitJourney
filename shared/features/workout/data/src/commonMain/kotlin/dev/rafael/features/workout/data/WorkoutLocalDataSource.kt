package dev.rafael.features.workout.data

import dev.rafael.contract.workout.WorkoutDto
import dev.rafael.core.database.FitJourneyDatabase
import kotlinx.serialization.json.Json

/** Cache do detalhe de um treino (leitura offline). Chave "workout:{id}" no kv_cache. */
class WorkoutLocalDataSource(db: FitJourneyDatabase) {
    private val cache = db.cacheQueries
    private val json = Json { ignoreUnknownKeys = true }

    fun save(id: String, dto: WorkoutDto) {
        cache.put("workout:$id", json.encodeToString(WorkoutDto.serializer(), dto))
    }

    fun read(id: String): WorkoutDto? {
        val payload = cache.get("workout:$id").executeAsOneOrNull() ?: return null
        return runCatching { json.decodeFromString(WorkoutDto.serializer(), payload) }.getOrNull()
    }
}
