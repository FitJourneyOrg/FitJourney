package dev.rafael.server.features.exercise.db

import dev.rafael.contract.exercise.ExerciseCategory
import dev.rafael.core.result.AppResult
import dev.rafael.server.features.exercise.models.Exercise
import kotlin.uuid.Uuid

interface ExerciseRepository {
    suspend fun findAll(): AppResult<List<Exercise>>
    suspend fun findByCategory(category: ExerciseCategory): AppResult<List<Exercise>>
    suspend fun findById(id: Uuid): AppResult<Exercise?>

    suspend fun existsByIds(ids: List<Uuid>): AppResult<Boolean>

}