package dev.rafael.server.features.program.services

import dev.rafael.contract.program.ProgramDto
import dev.rafael.contract.workout.WorkoutDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProgramActiveWorkoutTest {

    private fun programa(vararg treinos: WorkoutDto) = ProgramDto(
        id = "prog-1", name = "Programa", daysPerWeek = treinos.size, workouts = treinos.toList(),
    )

    @Test
    fun `marca isActive so no treino cujo id bate com o ponteiro`() {
        val ativo = WorkoutDto(id = "w-1", name = "Push")
        val outro = WorkoutDto(id = "w-2", name = "Pull")

        val resultado = ProgramActiveWorkout.apply(programa(ativo, outro), activeWorkoutId = "w-1")

        assertTrue(resultado.workouts.first { it.id == "w-1" }.isActive)
        assertFalse(resultado.workouts.first { it.id == "w-2" }.isActive)
    }

    @Test
    fun `ponteiro nulo nao marca nenhum treino e devolve o programa igual`() {
        val treino = WorkoutDto(id = "w-1", name = "Push")

        val resultado = ProgramActiveWorkout.apply(programa(treino), activeWorkoutId = null)

        assertFalse(resultado.workouts.first().isActive)
        assertEquals(programa(treino), resultado)
    }

    @Test
    fun `ponteiro que nao bate com nenhum treino deste programa nao marca nada`() {
        val treino = WorkoutDto(id = "w-1", name = "Push")

        val resultado = ProgramActiveWorkout.apply(programa(treino), activeWorkoutId = "w-de-outro-programa")

        assertFalse(resultado.workouts.first().isActive)
    }
}
