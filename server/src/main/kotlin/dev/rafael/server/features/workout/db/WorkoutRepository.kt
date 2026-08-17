package dev.rafael.server.features.workout.db

import dev.rafael.core.result.AppResult
import dev.rafael.server.features.workout.models.Workout
import dev.rafael.server.features.workout.models.WorkoutSummary
import kotlin.uuid.Uuid

interface WorkoutRepository {
    /**
     * programId/dayOfWeek (ARCH #27): todo treino vive dentro de um programa.
     *
     * IDEMPOTENTE quando `workout.id` vem preenchido (outbox do cliente, ARCH #30): reenviar o
     * mesmo id devolve o treino existente em vez de duplicar.
     *
     * `null` = o id já existe e pertence a OUTRO usuário → o service traduz em Conflict.
     */
    suspend fun create(userId: Uuid, workout: Workout, programId: Uuid, dayOfWeek: Int): AppResult<Workout?>
    suspend fun findAllByUser(userId: Uuid): AppResult<List<WorkoutSummary>>
    suspend fun findById(userId: Uuid, workoutId: Uuid): AppResult<Workout?>
    suspend fun update(userId: Uuid, workoutId: Uuid, workout: Workout): AppResult<Workout?>
    suspend fun delete(userId: Uuid, workoutId: Uuid): AppResult<Boolean>
}