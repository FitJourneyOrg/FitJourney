package dev.rafael.features.program.data

import dev.rafael.contract.program.ProgramDto
import dev.rafael.contract.program.ScheduleEntry
import dev.rafael.contract.workout.WorkoutDto
import dev.rafael.contract.workout.WorkoutExerciseDto
import dev.rafael.contract.workout.WorkoutSetDto
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Testa ProgramDto.toDomain() — puro, sem banco/rede.
 * Prova que os campos passam corretos e que ProgramWorkout guarda a CONTAGEM de
 * exercícios (não a lista completa — o detalhe vive na feature workout).
 */
class ProgramMapperTest {

    private fun exDto(i: Int) = WorkoutExerciseDto(
        exerciseId = "ex-$i",
        orderIndex = i,
        sets = listOf(WorkoutSetDto(reps = 10, orderIndex = 0)),
    )

    private fun sampleDto() = ProgramDto(
        id = "prog-1",
        name = "Meu programa",
        daysPerWeek = 4,
        split = "Upper/Lower",
        rationale = "porque sim",
        locked = true,
        workouts = listOf(
            WorkoutDto(id = "w1", name = "Upper", exercises = listOf(exDto(0), exDto(1), exDto(2))),
            WorkoutDto(id = "w2", name = "Lower", exercises = listOf(exDto(0))),
        ),
        schedule = listOf(
            ScheduleEntry(workoutId = "w1", dayOfWeek = 1),
            ScheduleEntry(workoutId = "w2", dayOfWeek = 3),
        ),
    )

    @Test
    fun `campos do programa passam direto`() {
        val d = sampleDto().toDomain()
        assertEquals("prog-1", d.id)
        assertEquals("Meu programa", d.name)
        assertEquals(4, d.daysPerWeek)
        assertEquals("Upper/Lower", d.split)
        assertEquals("porque sim", d.rationale)
        assertEquals(true, d.locked)
    }

    @Test
    fun `cada workout vira ProgramWorkout com a contagem de exercicios`() {
        val d = sampleDto().toDomain()
        assertEquals(2, d.workouts.size)
        assertEquals("w1", d.workouts[0].id)
        assertEquals("Upper", d.workouts[0].name)
        assertEquals(3, d.workouts[0].exerciseCount, "Upper tem 3 exercícios")
        assertEquals(1, d.workouts[1].exerciseCount, "Lower tem 1 exercício")
    }

    @Test
    fun `schedule mapeia workoutId e dia`() {
        val d = sampleDto().toDomain()
        assertEquals(2, d.schedule.size)
        assertEquals("w1", d.schedule[0].workoutId)
        assertEquals(1, d.schedule[0].dayOfWeek)
        assertEquals(3, d.schedule[1].dayOfWeek)
    }

    @Test
    fun `programa sem workouts vira dominio vazio`() {
        val d = ProgramDto(
            name = "Vazio", daysPerWeek = 0, split = "Manual", rationale = "",
        ).toDomain()
        assertEquals(0, d.workouts.size)
        assertEquals(0, d.schedule.size)
    }
}
