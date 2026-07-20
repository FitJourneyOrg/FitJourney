package dev.rafael.server.features.workout.models

import dev.rafael.contract.workout.WorkoutDto
import dev.rafael.contract.workout.WorkoutExerciseDto
import dev.rafael.contract.workout.WorkoutSetDto
import dev.rafael.contract.workout.WorkoutSummaryDto
import kotlin.uuid.Uuid




// domínio -> DTO (resposta): LER o campo
fun WorkoutExercise.toDto(): WorkoutExerciseDto = WorkoutExerciseDto(
    exerciseId = exerciseId.toString(),
    orderIndex = orderIndex,
    restSeconds = restSeconds,   // <- ERA O QUE FALTAVA (saía sempre 90)
    sets = sets.map { WorkoutSetDto(reps = it.reps, orderIndex = it.orderIndex) },
)
// DTO -> domínio (entrada): ESCREVER o campo
fun WorkoutExerciseDto.toDomain(): WorkoutExercise = WorkoutExercise(
    id = Uuid.NIL,
    exerciseId = Uuid.parse(exerciseId),
    orderIndex = orderIndex,
    restSeconds = restSeconds,   // <- ERA O QUE FALTAVA (valor do cliente morria aqui)
    sets = sets.map { WorkoutSet(id = Uuid.NIL, reps = it.reps, orderIndex = it.orderIndex) },
)



fun WorkoutSummary.toDto(): WorkoutSummaryDto = WorkoutSummaryDto(
    id = id.toString(),
    name = name,
    exerciseCount = exerciseCount,
    updatedAt = updatedAt.toString(),
)



// ---- domínio -> DTO (resposta) ----
fun Workout.toDto(): WorkoutDto = WorkoutDto(
    id = id.toString(),
    name = name,
    exercises = exercises.map { it.toDto() },
    createdAt = createdAt.toString(),
    updatedAt = updatedAt.toString(),
)

fun WorkoutDto.toDomain(): Workout = Workout(
    id = Uuid.NIL,
    userId = Uuid.NIL,
    name = name,
    exercises = exercises.map { it.toDomain() },
    createdAt = kotlinx.datetime.LocalDateTime(1970, 1, 1, 0, 0),
    updatedAt = kotlinx.datetime.LocalDateTime(1970, 1, 1, 0, 0),
)

