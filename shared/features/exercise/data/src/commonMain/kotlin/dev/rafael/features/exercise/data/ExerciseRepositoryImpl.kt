package dev.rafael.features.exercise.data

import dev.rafael.contract.exercise.ExerciseCategory
import dev.rafael.core.network.httpResult
import dev.rafael.core.result.AppError
import dev.rafael.core.result.AppResult
import dev.rafael.features.exercise.domain.model.Exercise
import dev.rafael.features.exercise.domain.repository.ExerciseRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ExerciseRepositoryImpl(
    private val remote: ExerciseRemoteDataSource,
    private val local: ExerciseLocalDataSource,
) : ExerciseRepository {

    override fun observeExercises(category: ExerciseCategory?): Flow<List<Exercise>> {
        val rows = if (category == null) local.observeAll()
        else local.observeByCategory(category.name)
        return rows.map { list -> list.mapNotNull { it.toDomainOrNull() } }
    }

    override suspend fun refresh(): AppResult<Unit> =
        httpResult { local.replaceAll(remote.getExercises(category = null)) }

    override suspend fun alternatives(exerciseId: String): AppResult<List<Exercise>> =
        httpResult { remote.getAlternatives(exerciseId).map { it.toDomain() } }

    override suspend fun getDetail(exerciseId: String): AppResult<Exercise> =
        when (val r = httpResult { remote.getExercises(category = null) }) {
            is AppResult.Success ->
                r.value.firstOrNull { it.id == exerciseId }?.toDomain()
                    ?.let { AppResult.Success(it) }
                    ?: AppResult.Failure(AppError.NotFound())
            is AppResult.Failure -> r
        }
}