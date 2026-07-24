package dev.rafael.features.workout.data

import dev.rafael.contract.workout.WorkoutDto
import dev.rafael.contract.workout.WorkoutExerciseDto
import dev.rafael.contract.workout.WorkoutSetDto
import dev.rafael.contract.workout.WorkoutSummaryDto
import dev.rafael.features.workout.domain.model.Workout
import dev.rafael.features.workout.domain.model.WorkoutExercise
import dev.rafael.features.workout.domain.model.WorkoutSet
import dev.rafael.features.workout.domain.model.WorkoutSummary

// DTO -> domain
fun WorkoutSummaryDto.toDomain() = WorkoutSummary(id, name, exerciseCount, updatedAt)

fun WorkoutDto.toDomain() = Workout(
    id = id, name = name,
    programId = programId,
    exercises = exercises.map { it.toDomain() },
    createdAt = createdAt, updatedAt = updatedAt,
)

// argumentos NOMEADOS: imuniza contra a família de bug "campo novo perdido no ponto de construção"
private fun WorkoutExerciseDto.toDomain() = WorkoutExercise(
    exerciseId = exerciseId,
    orderIndex = orderIndex,
    restSeconds = restSeconds,   // lê o campo do DTO
    sets = sets.map { it.toDomain() },
)
private fun WorkoutSetDto.toDomain() = WorkoutSet(reps, orderIndex)

// domain -> DTO (para create/update)
fun Workout.toDto() = WorkoutDto(
    id = id, name = name,
    programId = programId,
    exercises = exercises.map { it.toDto() },
    createdAt = createdAt, updatedAt = updatedAt,
)
private fun WorkoutExercise.toDto() = WorkoutExerciseDto(
    exerciseId = exerciseId,
    orderIndex = orderIndex,
    restSeconds = restSeconds,   // escreve o campo (era o vazio)
    sets = sets.map { it.toDto() },
)
private fun WorkoutSet.toDto() = WorkoutSetDto(reps, orderIndex)