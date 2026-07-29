package dev.rafael.server.features.program.services

import dev.rafael.contract.program.ProgramDto
import dev.rafael.contract.workout.WorkoutDto
import dev.rafael.contract.workout.WorkoutExerciseDto
import dev.rafael.contract.workout.WorkoutOrigin
import dev.rafael.contract.workout.WorkoutSetDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Testa a política de blur/value-first (ARCH #23) — pura. Não-premium: Dia 1 livre,
 * dias > 0 trancados e esvaziados (com contador). Premium e programa manual veem tudo.
 */
class ProgramBlurTest {

    private fun ex() =
        WorkoutExerciseDto(exerciseId = "e", orderIndex = 0, sets = listOf(WorkoutSetDto(reps = 10, orderIndex = 0)))

    private fun program(origin: WorkoutOrigin, days: Int) = ProgramDto(
        name = "P", origin = origin, daysPerWeek = days, split = "PPL", rationale = "r",
        workouts = (1..days).map { WorkoutDto(name = "Dia $it", origin = origin, exercises = List(5) { ex() }) },
    )

    @Test
    fun `nao-premium tranca dias apos o primeiro no programa IA`() {
        val out = ProgramBlur.apply(program(WorkoutOrigin.AI, 3), isPremium = false)

        assertTrue(out.locked, "programa marcado com conteúdo trancado")
        // Dia 1 livre
        assertFalse(out.workouts[0].locked)
        assertEquals(5, out.workouts[0].exercises.size)
        // Dias 2+ trancados, esvaziados, com contador preservado
        assertTrue(out.workouts[1].locked)
        assertTrue(out.workouts[1].exercises.isEmpty())
        assertEquals(5, out.workouts[1].lockedExerciseCount)
        assertTrue(out.workouts[2].locked)
    }

    @Test
    fun `premium ve o programa inteiro`() {
        val out = ProgramBlur.apply(program(WorkoutOrigin.AI, 3), isPremium = true)
        assertFalse(out.locked)
        assertTrue(out.workouts.all { !it.locked && it.exercises.size == 5 })
    }

    @Test
    fun `programa manual nao e borrado nem p-nao-premium`() {
        val out = ProgramBlur.apply(program(WorkoutOrigin.MANUAL, 3), isPremium = false)
        assertFalse(out.locked)
        assertTrue(out.workouts.all { !it.locked && it.exercises.size == 5 })
    }
}
