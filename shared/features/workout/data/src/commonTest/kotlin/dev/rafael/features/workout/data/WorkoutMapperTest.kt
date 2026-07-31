package dev.rafael.features.workout.data

import dev.rafael.contract.workout.WorkoutDto
import dev.rafael.contract.workout.WorkoutExerciseDto
import dev.rafael.contract.workout.WorkoutSetDto
import dev.rafael.contract.workout.WorkoutSummaryDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Testa os mappers de Workout — puro. Foco no que já quebrou antes:
 * restSeconds e rir NÃO podem sumir no DTO<->domínio (bug "campo vazio").
 */
class WorkoutMapperTest {

    private fun exDto() = WorkoutExerciseDto(
        exerciseId = "ex-1", orderIndex = 0, restSeconds = 120, rir = 2,
        sets = listOf(WorkoutSetDto(reps = 8, orderIndex = 0), WorkoutSetDto(reps = 10, orderIndex = 1)),
    )

    private fun dto() = WorkoutDto(
        id = "w1", name = "Upper", programId = "prog-1",
        exercises = listOf(exDto()),
    )

    @Test
    fun `toDomain preserva restSeconds e rir do exercicio`() {
        val d = dto().toDomain()
        assertEquals("w1", d.id)
        assertEquals("prog-1", d.programId)
        assertEquals(1, d.exercises.size)
        assertEquals(120, d.exercises[0].restSeconds, "restSeconds não pode sumir")
        assertEquals(2, d.exercises[0].rir)
        assertEquals(2, d.exercises[0].sets.size)
        assertEquals(8, d.exercises[0].sets[0].reps)
    }

    @Test
    fun `round-trip DTO para dominio para DTO nao perde restSeconds nem rir`() {
        val original = dto()
        val roundTrip = original.toDomain().toDto()
        assertEquals(original.exercises[0].restSeconds, roundTrip.exercises[0].restSeconds)
        assertEquals(original.exercises[0].rir, roundTrip.exercises[0].rir)
        assertEquals(original.programId, roundTrip.programId)
    }

    @Test
    fun `rir nulo (treino manual) sobrevive ao mapeamento`() {
        val manual = dto().copy(exercises = listOf(exDto().copy(rir = null)))
        assertNull(manual.toDomain().exercises[0].rir)
    }

    @Test
    fun `summary mapeia contagem e nome`() {
        val s = WorkoutSummaryDto(id = "w1", name = "Upper", exerciseCount = 5, updatedAt = "2026-01-01").toDomain()
        assertEquals("w1", s.id)
        assertEquals("Upper", s.name)
        assertEquals(5, s.exerciseCount)
    }
}
