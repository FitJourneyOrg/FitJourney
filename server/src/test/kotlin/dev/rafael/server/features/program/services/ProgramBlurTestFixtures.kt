package dev.rafael.server.features.program.services

import dev.rafael.contract.program.ProgramDto
import dev.rafael.contract.workout.WorkoutDto
import dev.rafael.contract.workout.WorkoutExerciseDto
import dev.rafael.contract.workout.WorkoutOrigin
import dev.rafael.contract.workout.WorkoutSetDto

/** Programa de teste compartilhado entre ProgramBlurTest e ProgramAccessTest. */
object ProgramBlurTestFixtures {

    private fun exercicio() = WorkoutExerciseDto(
        exerciseId = "e",
        orderIndex = 0,
        sets = listOf(WorkoutSetDto(reps = 10, orderIndex = 0)),
    )

    fun programaIa(dias: Int): ProgramDto = ProgramDto(
        name = "P",
        origin = WorkoutOrigin.AI,
        daysPerWeek = dias,
        split = "PPL",
        rationale = "r",
        workouts = (1..dias).map {
            WorkoutDto(name = "Dia $it", origin = WorkoutOrigin.AI, exercises = List(5) { exercicio() })
        },
    )
}
