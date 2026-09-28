package dev.rafael.server.features.program.services

import dev.rafael.contract.program.ProgramDto
import dev.rafael.contract.workout.WorkoutDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

/** Clock fixo -- 2026-09-28 é uma SEGUNDA (isoDayNumber 1). */
private val SEGUNDA: Clock = object : Clock {
    override fun now() = Instant.parse("2026-09-28T10:00:00Z")
}

class ProgramActiveWorkoutTest {

    private fun programa(id: String = "prog-1", vararg treinos: WorkoutDto) = ProgramDto(
        id = id, name = "Programa", daysPerWeek = treinos.size, workouts = treinos.toList(),
    )

    @Test
    fun `programa ativo marca isActive no treino cujo dayOfWeek bate com hoje`() {
        val deHoje = WorkoutDto(id = "w-1", name = "Push", dayOfWeek = 1)
        val deOutroDia = WorkoutDto(id = "w-2", name = "Pull", dayOfWeek = 2)

        val resultado = ProgramActiveWorkout.apply(programa("prog-1", deHoje, deOutroDia), "prog-1", SEGUNDA)

        assertTrue(resultado.isActive, "o programa marcado como ativo deve vir com isActive=true")
        assertTrue(resultado.workouts.first { it.id == "w-1" }.isActive)
        assertFalse(resultado.workouts.first { it.id == "w-2" }.isActive)
    }

    @Test
    fun `programa ativo em dia de descanso nao marca nenhum treino`() {
        val treino = WorkoutDto(id = "w-1", name = "Push", dayOfWeek = 3)   // quarta, não hoje

        val resultado = ProgramActiveWorkout.apply(programa("prog-1", treino), "prog-1", SEGUNDA)

        assertTrue(resultado.isActive, "o programa continua ativo mesmo sem treino hoje")
        assertFalse(resultado.workouts.first().isActive)
    }

    @Test
    fun `ponteiro nulo nao marca o programa nem nenhum treino`() {
        val treino = WorkoutDto(id = "w-1", name = "Push", dayOfWeek = 1)

        val resultado = ProgramActiveWorkout.apply(programa("prog-1", treino), null, SEGUNDA)

        assertFalse(resultado.isActive)
        assertFalse(resultado.workouts.first().isActive)
        assertEquals(programa("prog-1", treino), resultado)
    }

    @Test
    fun `ponteiro de OUTRO programa nao marca nada neste`() {
        val treino = WorkoutDto(id = "w-1", name = "Push", dayOfWeek = 1)

        val resultado = ProgramActiveWorkout.apply(programa("prog-1", treino), "prog-de-outro", SEGUNDA)

        assertFalse(resultado.isActive)
        assertFalse(resultado.workouts.first().isActive)
    }
}
