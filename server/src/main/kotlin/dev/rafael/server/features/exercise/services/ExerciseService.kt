package dev.rafael.server.features.exercise.services

import dev.rafael.contract.exercise.ExerciseCategory
import dev.rafael.contract.exercise.ExerciseDto
import dev.rafael.core.result.AppResult
import dev.rafael.core.result.map
import dev.rafael.server.features.exercise.db.ExerciseRepository
import dev.rafael.server.features.exercise.models.toDto

class ExerciseService(private val repository: ExerciseRepository) {

    suspend fun listAll(): AppResult<List<ExerciseDto>> =
        repository.findAll().map { list -> list.map { it.toDto() } }

    suspend fun listByCategory(category: ExerciseCategory): AppResult<List<ExerciseDto>> =
        repository.findByCategory(category).map { list -> list.map { it.toDto() } }
}