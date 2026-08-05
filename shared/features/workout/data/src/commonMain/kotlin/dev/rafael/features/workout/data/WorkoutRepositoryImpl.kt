package dev.rafael.features.workout.data

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
) : WorkoutRepository {

    override suspend fun list(): AppResult<List<WorkoutSummary>> =
        httpResult { remote.list().map { it.toDomain() } }

    // Offline-first: rede → cacheia + retorna; falha de REDE → cai no cache (p/ treinar offline).
    override suspend fun get(id: String): AppResult<Workout> =
        when (val net = httpResult { remote.get(id) }) {
            is AppResult.Success -> {
                local.save(id, net.value)
                net.value.toDomain().asSuccess()
            }
            is AppResult.Failure -> {
                val cached = if (net.error is AppError.Unexpected) local.read(id) else null
                cached?.toDomain()?.asSuccess() ?: net.error.asFailure()
            }
        }

    override suspend fun create(workout: Workout): AppResult<Workout> =
        httpResult { remote.create(workout.toDto()).toDomain() }

    override suspend fun update(id: String, workout: Workout): AppResult<Workout> =
        httpResult { remote.update(id, workout.toDto()).toDomain() }

    override suspend fun delete(id: String): AppResult<Unit> =
        httpResult { remote.delete(id) }
}
