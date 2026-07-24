package dev.rafael.features.program.data

import dev.rafael.contract.program.ProgramDto
import dev.rafael.contract.program.ScheduleEntry
import dev.rafael.contract.workout.WorkoutDto
import dev.rafael.features.program.domain.model.Program
import dev.rafael.features.program.domain.model.ProgramScheduleEntry
import dev.rafael.features.program.domain.model.ProgramWorkout

fun ProgramDto.toDomain() = Program(
    id = id,
    name = name,
    workouts = workouts.map { it.toProgramWorkout() },
    daysPerWeek = daysPerWeek,
    split = split,
    rationale = rationale,
    locked = locked,
    schedule = schedule.map { it.toDomain() },
    createdAt = createdAt,
    updatedAt = updatedAt,
)

// Mapper próprio (não reusa workout:data — ver build.gradle.kts) — só o que a
// tela de programa precisa mostrar; detalhe completo vive na feature workout.
private fun WorkoutDto.toProgramWorkout() = ProgramWorkout(
    id = id,
    name = name,
    exerciseCount = exercises.size,
)

private fun ScheduleEntry.toDomain() = ProgramScheduleEntry(workoutId = workoutId, dayOfWeek = dayOfWeek)
