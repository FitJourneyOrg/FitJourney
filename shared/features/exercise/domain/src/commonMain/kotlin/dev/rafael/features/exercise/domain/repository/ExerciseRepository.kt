package dev.rafael.features.exercise.domain.repository

import dev.rafael.contract.exercise.ExerciseCategory
import dev.rafael.core.result.AppResult
import dev.rafael.features.exercise.domain.model.Exercise
import kotlinx.coroutines.flow.Flow

interface ExerciseRepository {
    fun observeExercises(category: ExerciseCategory?): Flow<List<Exercise>>
    suspend fun refresh(): AppResult<Unit>
}