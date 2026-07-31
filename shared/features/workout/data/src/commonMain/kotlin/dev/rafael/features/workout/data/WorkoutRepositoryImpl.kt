package dev.rafael.features.workout.data

import dev.rafael.core.network.httpResult
import dev.rafael.core.result.AppResult
import dev.rafael.features.workout.domain.model.Workout
import dev.rafael.features.workout.domain.model.WorkoutSummary
import dev.rafael.features.workout.domain.repository.WorkoutRepository

class WorkoutRepositoryImpl(
    private val remote: WorkoutDataSource,
) : WorkoutRepository {

    override suspend fun list(): AppResult<List<WorkoutSummary>> =
        httpResult { remote.list().map { it.toDomain() } }

    override suspend fun get(id: String): AppResult<Workout> =
        httpResult { remote.get(id).toDomain() }

    override suspend fun create(workout: Workout): AppResult<Workout> =
        httpResult { remote.create(workout.toDto()).toDomain() }

    override suspend fun update(id: String, workout: Workout): AppResult<Workout> =
        httpResult { remote.update(id, workout.toDto()).toDomain() }

    override suspend fun delete(id: String): AppResult<Unit> =
        httpResult { remote.delete(id) }
}
