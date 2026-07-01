package dev.rafael.server.features.exercise.models

import dev.rafael.contract.exercise.ExerciseDto

fun Exercise.toDto(): ExerciseDto = ExerciseDto(
    id = id.toString(),
    name = name,
    category = category,
    description = description,
    videoRef = videoRef,
    thumbRef = thumbRef,
)