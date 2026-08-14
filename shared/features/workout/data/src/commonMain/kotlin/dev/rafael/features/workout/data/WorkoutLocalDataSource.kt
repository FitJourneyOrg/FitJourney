package dev.rafael.features.workout.data

import dev.rafael.contract.workout.WorkoutDto
import dev.rafael.contract.workout.WorkoutExerciseDto
import dev.rafael.contract.workout.WorkoutOrigin
import dev.rafael.core.database.FitJourneyDatabase
import dev.rafael.core.network.TokenProvider
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * Armazenamento local do detalhe do treino — TABELA REAL `workout` (ARCH #30, passo 4).
 *
 * Antes era um blob no kv_cache com chave "workout:{id}". Agora divide a mesma tabela que o
 * sync de programas popula: o GET /programs já traz os treinos aninhados, então abrir um
 * treino offline funciona mesmo sem nunca tê-lo aberto online.
 *
 * [REGRA] chaveado por uid (ARCH #30).
 */
class WorkoutLocalDataSource(
    db: FitJourneyDatabase,
    private val tokenProvider: TokenProvider,
) {
    private val q = db.workoutQueries
    private val json = Json { ignoreUnknownKeys = true }
    private val exerciciosSerializer = ListSerializer(WorkoutExerciseDto.serializer())

    private suspend fun uid(): String = tokenProvider.currentUid() ?: ""

    suspend fun save(id: String, dto: WorkoutDto) {
        q.salvarTreino(
            id = id,
            uid = uid(),
            programId = dto.programId,
            name = dto.name,
            origin = dto.origin.name,
            locked = if (dto.locked) 1L else 0L,
            lockedExerciseCount = dto.lockedExerciseCount.toLong(),
            exerciseCount = dto.exercises.size.toLong(),
            exercisesJson = json.encodeToString(exerciciosSerializer, dto.exercises),
        )
    }

    suspend fun read(id: String): WorkoutDto? {
        val linha = q.lerTreino(id, uid()).executeAsOneOrNull() ?: return null
        return WorkoutDto(
            id = linha.id,
            name = linha.name,
            origin = runCatching { WorkoutOrigin.valueOf(linha.origin) }.getOrDefault(WorkoutOrigin.AI),
            programId = linha.programId,
            exercises = runCatching {
                json.decodeFromString(exerciciosSerializer, linha.exercisesJson)
            }.getOrDefault(emptyList()),
            locked = linha.locked == 1L,
            lockedExerciseCount = linha.lockedExerciseCount.toInt(),
        )
    }
}
