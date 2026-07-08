package dev.rafael.features.workout.domain.repository

import dev.rafael.core.result.AppResult
import dev.rafael.features.workout.domain.model.Workout
import dev.rafael.features.workout.domain.model.WorkoutSummary

interface WorkoutRepository {
    suspend fun list(): AppResult<List<WorkoutSummary>>          // GET /workouts
    suspend fun get(id: String): AppResult<Workout>             // GET /workouts/{id}
    suspend fun create(workout: Workout): AppResult<Workout>    // POST /workouts
    suspend fun update(id: String, workout: Workout): AppResult<Workout>  // PUT /workouts/{id}
    suspend fun delete(id: String): AppResult<Unit>             // DELETE /workouts/{id}
}