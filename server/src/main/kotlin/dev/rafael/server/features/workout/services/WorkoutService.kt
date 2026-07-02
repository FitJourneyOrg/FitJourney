package dev.rafael.server.features.workout.services

import dev.rafael.contract.workout.WorkoutDto
import dev.rafael.contract.workout.WorkoutSummaryDto
import dev.rafael.core.result.AppError
import dev.rafael.core.result.AppResult
import dev.rafael.core.result.asFailure
import dev.rafael.core.result.flatMap
import dev.rafael.core.result.getOrNull
import dev.rafael.core.result.map
import dev.rafael.server.features.exercise.db.ExerciseRepository
import dev.rafael.server.features.user.services.UserService
import dev.rafael.server.features.workout.db.WorkoutRepository
import dev.rafael.server.features.workout.models.toDomain
import dev.rafael.server.features.workout.models.toDto
import kotlin.uuid.Uuid

class WorkoutService(
    private val userService: UserService,
    private val repository: WorkoutRepository,
    private val exerciseRepository: ExerciseRepository,
) {

    suspend fun create(firebaseUid: String, email: String?, dto: WorkoutDto): AppResult<WorkoutDto> {
        validate(dto)?.let { return it.asFailure() }
        validateExercisesExist(dto)?.let { return it.asFailure() }
        return userService.findOrCreate(firebaseUid, email).flatMap { user ->
            repository.create(user.id, dto.toDomain()).map { it.toDto() }
        }
    }

    suspend fun list(firebaseUid: String, email: String?): AppResult<List<WorkoutSummaryDto>> =
        userService.findOrCreate(firebaseUid, email).flatMap { user ->
            repository.findAllByUser(user.id).map { list -> list.map { it.toDto() } }
        }

    suspend fun get(firebaseUid: String, email: String?, workoutId: Uuid): AppResult<WorkoutDto?> =
        userService.findOrCreate(firebaseUid, email).flatMap { user ->
            repository.findById(user.id, workoutId).map { it?.toDto() }
        }

    suspend fun update(firebaseUid: String, email: String?, workoutId: Uuid, dto: WorkoutDto): AppResult<WorkoutDto?> {
        validate(dto)?.let { return it.asFailure() }
        validateExercisesExist(dto)?.let { return it.asFailure() }
        return userService.findOrCreate(firebaseUid, email).flatMap { user ->
            repository.update(user.id, workoutId, dto.toDomain()).map { it?.toDto() }
        }
    }

    suspend fun delete(firebaseUid: String, email: String?, workoutId: Uuid): AppResult<Boolean> =
        userService.findOrCreate(firebaseUid, email).flatMap { user ->
            repository.delete(user.id, workoutId)
        }

    private fun validate(dto: WorkoutDto): AppError? {
        if (dto.name.isBlank()) return AppError.Validation("Nome do treino é obrigatório")
        if (dto.exercises.isEmpty()) return AppError.Validation("Treino precisa de ao menos 1 exercício")
        dto.exercises.forEach { ex ->
            if (ex.sets.isEmpty()) return AppError.Validation("Cada exercício precisa de ao menos 1 série")
            ex.sets.forEach { s ->
                if (s.reps <= 0) return AppError.Validation("Repetições devem ser maiores que zero")
            }
        }
        return null
    }

    private suspend fun validateExercisesExist(dto: WorkoutDto): AppError? {
        val ids = try {
            dto.exercises.map { Uuid.parse(it.exerciseId) }
        } catch (e: IllegalArgumentException) {
            return AppError.Validation("ID de exercício inválido")
        }
        val allExist = exerciseRepository.existsByIds(ids).getOrNull() ?: false
        return if (allExist) null
        else AppError.Validation("Um ou mais exercícios não existem no catálogo")
    }
}