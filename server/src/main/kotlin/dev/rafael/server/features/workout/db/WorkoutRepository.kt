package dev.rafael.server.features.workout.db

import dev.rafael.core.result.AppResult
import dev.rafael.server.features.workout.models.Workout
import dev.rafael.server.features.workout.models.WorkoutSummary
import kotlin.uuid.Uuid

interface WorkoutRepository {
    /** programId/dayOfWeek (ARCH #26): todo treino vive dentro de um programa. */
    suspend fun create(userId: Uuid, workout: Workout, programId: Uuid, dayOfWeek: Int): AppResult<Workout>
    suspend fun findAllByUser(userId: Uuid): AppResult<List<WorkoutSummary>>
    suspend fun findById(userId: Uuid, workoutId: Uuid): AppResult<Workout?>
    suspend fun update(userId: Uuid, workoutId: Uuid, workout: Workout): AppResult<Workout?>
    suspend fun delete(userId: Uuid, workoutId: Uuid): AppResult<Boolean>
}