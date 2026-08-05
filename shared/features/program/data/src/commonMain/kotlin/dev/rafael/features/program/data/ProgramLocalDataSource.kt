package dev.rafael.features.program.data

import dev.rafael.contract.program.ProgramDto
import dev.rafael.contract.workout.WorkoutDto
import dev.rafael.core.database.FitJourneyDatabase
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** Cache da lista de programas (leitura offline). Chave única "programs" no kv_cache. */
class ProgramLocalDataSource(db: FitJourneyDatabase) {
    private val cache = db.cacheQueries
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(ProgramDto.serializer())

    fun save(programs: List<ProgramDto>) {
        cache.put(KEY, json.encodeToString(serializer, programs))
        // Cacheia CADA treino embutido (o GET /programs já os traz com exercícios), na mesma
        // chave que o workout:data lê ("workout:{id}"). Assim todos ficam offline sem precisar
        // abrir cada um individualmente. (Treino trancado vem sem exercícios — mas nem abre offline.)
        programs.forEach { p ->
            p.workouts.forEach { w ->
                val id = w.id ?: return@forEach
                cache.put("workout:$id", json.encodeToString(WorkoutDto.serializer(), w))
            }
        }
    }

    fun read(): List<ProgramDto>? {
        val payload = cache.get(KEY).executeAsOneOrNull() ?: return null
        return runCatching { json.decodeFromString(serializer, payload) }.getOrNull()
    }

    private companion object {
        const val KEY = "programs"
    }
}
