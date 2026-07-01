package dev.rafael.server.features.exercise.db

import dev.rafael.contract.exercise.ExerciseCategory
import dev.rafael.core.result.AppError
import dev.rafael.core.result.AppResult
import dev.rafael.core.result.asFailure
import dev.rafael.core.result.asSuccess
import dev.rafael.server.features.exercise.models.Exercise
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

class ExerciseRepositoryImpl : ExerciseRepository {

    override suspend fun findAll(): AppResult<List<Exercise>> =
        dbQuery {
            ExercisesTable.selectAll().map { it.toExercise() }
        }

    override suspend fun findByCategory(category: ExerciseCategory): AppResult<List<Exercise>> =
        dbQuery {
            ExercisesTable.selectAll()
                .where { ExercisesTable.category eq category.name }
                .map { it.toExercise() }
        }

    private suspend fun <T> dbQuery(block: () -> T): AppResult<T> =
        withContext(Dispatchers.IO) {
            runCatching { transaction { block() } }.fold(
                onSuccess = { it.asSuccess() },
                onFailure = { AppError.Unexpected("Erro de banco", it).asFailure() },
            )
        }
}

private fun ResultRow.toExercise(): Exercise = Exercise(
    id = this[ExercisesTable.id],
    name = this[ExercisesTable.name],
    category = dev.rafael.contract.exercise.ExerciseCategory.valueOf(this[ExercisesTable.category]),
    description = this[ExercisesTable.description],
    videoRef = this[ExercisesTable.videoRef],
    thumbRef = this[ExercisesTable.thumbRef],
)