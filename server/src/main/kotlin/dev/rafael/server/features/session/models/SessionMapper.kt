package dev.rafael.server.features.session.models

import dev.rafael.contract.session.SetLogDto
import dev.rafael.contract.session.WorkoutSessionDto
import kotlinx.datetime.LocalDateTime
import kotlin.uuid.Uuid

/** DTO (fio) -> domínio. userId vem do token (não confia no corpo). */
fun WorkoutSessionDto.toDomain(userId: Uuid): WorkoutSession = WorkoutSession(
    id = Uuid.parse(id),
    userId = userId,
    programId = programId?.let { Uuid.parse(it) },
    workoutId = workoutId?.let { Uuid.parse(it) },
    workoutName = workoutName,
    startedAt = LocalDateTime.parse(startedAt),
    finishedAt = LocalDateTime.parse(finishedAt),
    sets = sets.map { it.toDomain() },
)

private fun SetLogDto.toDomain() = SetLog(
    exerciseId = Uuid.parse(exerciseId),
    orderIndex = orderIndex,
    setIndex = setIndex,
    targetReps = targetReps,
    repsDone = repsDone,
    weightKg = weightKg,
    done = done,
)

fun WorkoutSession.toDto(): WorkoutSessionDto = WorkoutSessionDto(
    id = id.toString(),
    programId = programId?.toString(),
    workoutId = workoutId?.toString(),
    workoutName = workoutName,
    startedAt = startedAt.toString(),
    finishedAt = finishedAt.toString(),
    sets = sets.map { it.toDto() },
)

private fun SetLog.toDto() = SetLogDto(
    exerciseId = exerciseId.toString(),
    orderIndex = orderIndex,
    setIndex = setIndex,
    targetReps = targetReps,
    repsDone = repsDone,
    weightKg = weightKg,
    done = done,
)
