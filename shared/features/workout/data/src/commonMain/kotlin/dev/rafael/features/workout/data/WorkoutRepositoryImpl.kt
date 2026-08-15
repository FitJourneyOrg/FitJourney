package dev.rafael.features.workout.data

import dev.rafael.core.database.SyncStamps
import dev.rafael.core.network.httpResult
import dev.rafael.core.result.AppError
import dev.rafael.core.result.AppResult
import dev.rafael.core.result.asFailure
import dev.rafael.core.result.asSuccess
import dev.rafael.features.workout.domain.model.Workout
import dev.rafael.features.workout.domain.model.WorkoutSummary
import dev.rafael.features.workout.domain.repository.WorkoutRepository

class WorkoutRepositoryImpl(
    private val remote: WorkoutDataSource,
    private val local: WorkoutLocalDataSource,
    private val stamps: SyncStamps,
) : WorkoutRepository {

    override suspend fun list(): AppResult<List<WorkoutSummary>> =
        httpResult { remote.list().map { it.toDomain() } }

    /**
     * CACHE-FIRST ([REGRA] ARCH #30: a tela lê do banco, sync é de fundo).
     *
     * Antes isto era network-first — apesar do comentário dizer "offline-first", ia à rede em
     * TODA abertura e só usava o cache se a rede falhasse. O efeito no log do servidor: um
     * `GET /workouts/{id}` por navegação, mais um 403 a cada vez que a Home (viva no back
     * stack) reagia ao Flow e buscava o treino do dia trancado.
     */
    override suspend fun get(id: String): AppResult<Workout> {
        // Carimbo por treino E por uid (`sync:workout:{id}:{uid}`) — o isolamento vem da chave.
        if (stamps.fresco(SyncStamps.treino(id), TTL_MS)) {
            local.read(id)?.let { return it.toDomain().asSuccess() }
        }
        return refresh(id)
    }

    /** Vai à rede e regrava o local. Falha de TRANSPORTE cai no cache (p/ treinar offline). */
    private suspend fun refresh(id: String): AppResult<Workout> =
        when (val net = httpResult { remote.get(id) }) {
            is AppResult.Success -> {
                local.save(id, net.value)
                stamps.marcar(SyncStamps.treino(id))
                net.value.toDomain().asSuccess()
            }
            is AppResult.Failure -> {
                // Ver ProgramRepositoryImpl: só transporte cai no cache; resposta do servidor, não.
                val cached = if (net.error is AppError.Connection) local.read(id) else null
                cached?.toDomain()?.asSuccess() ?: net.error.asFailure()
            }
        }

    /** Muda o treino → o carimbo dele morre, e o próximo get() busca o estado novo. */
    private suspend fun invalidar(id: String) {
        stamps.invalidar(SyncStamps.treino(id))
    }

    override suspend fun create(workout: Workout): AppResult<Workout> =
        httpResult { remote.create(workout.toDto()).toDomain() }

    override suspend fun update(id: String, workout: Workout): AppResult<Workout> =
        httpResult { remote.update(id, workout.toDto()).toDomain() }.also { invalidar(id) }

    override suspend fun delete(id: String): AppResult<Unit> =
        httpResult { remote.delete(id) }.also { invalidar(id) }

    private companion object {
        /** Mesma janela do ProgramRepositoryImpl — política de frescor única no app. */
        const val TTL_MS = 5 * 60 * 1000L
    }
}
