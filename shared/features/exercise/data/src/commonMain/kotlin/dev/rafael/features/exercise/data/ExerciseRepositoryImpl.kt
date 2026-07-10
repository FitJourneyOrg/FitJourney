package dev.rafael.features.exercise.data

import dev.rafael.contract.exercise.ExerciseCategory
import dev.rafael.core.result.AppError
import dev.rafael.core.result.AppResult
import dev.rafael.core.result.asFailure
import dev.rafael.core.result.asSuccess
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
        runCatching {
            val dtos = remote.getExercises(category = null)
            local.replaceAll(dtos)
        }.fold(
            onSuccess = { Unit.asSuccess() },
            onFailure = { AppError.Unexpected("Falha ao atualizar catálogo de exercícios", it).asFailure() },
        )
}