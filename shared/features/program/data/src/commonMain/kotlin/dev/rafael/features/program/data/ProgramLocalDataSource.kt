package dev.rafael.features.program.data

import dev.rafael.contract.program.ProgramDto
import dev.rafael.contract.workout.WorkoutDto
import dev.rafael.core.database.FitJourneyDatabase
import dev.rafael.core.network.TokenProvider
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * Cache da lista de programas (leitura offline).
 *
 * [REGRA] A chave inclui o UID: o mesmo aparelho pode ter várias contas, e sem isso o
 * usuário novo enxergaria os programas do anterior.
 */
class ProgramLocalDataSource(
    db: FitJourneyDatabase,
    private val tokenProvider: TokenProvider,
) {
    private val cache = db.cacheQueries
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(ProgramDto.serializer())

    private suspend fun key() = "$KEY:${tokenProvider.currentUid() ?: ""}"
    private suspend fun workoutKey(id: String) = "workout:$id:${tokenProvider.currentUid() ?: ""}"

    suspend fun save(programs: List<ProgramDto>) {
        cache.put(key(), json.encodeToString(serializer, programs))
        // Cacheia CADA treino embutido (o GET /programs já os traz com exercícios), na mesma
        // chave que o workout:data lê ("workout:{id}"). Assim todos ficam offline sem precisar
        // abrir cada um individualmente. (Treino trancado vem sem exercícios — mas nem abre offline.)
        programs.forEach { p ->
            p.workouts.forEach { w ->
                val id = w.id ?: return@forEach
                cache.put(workoutKey(id), json.encodeToString(WorkoutDto.serializer(), w))
            }
        }
    }

    suspend fun read(): List<ProgramDto>? {
        val payload = cache.get(key()).executeAsOneOrNull() ?: return null
        return runCatching { json.decodeFromString(serializer, payload) }.getOrNull()
    }

    private companion object {
        const val KEY = "programs"
    }
}
