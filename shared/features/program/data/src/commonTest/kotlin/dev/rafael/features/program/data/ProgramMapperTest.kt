package dev.rafael.features.program.data

import dev.rafael.contract.profile.MuscleGroup
import dev.rafael.contract.profile.SplitType
import dev.rafael.contract.program.ProgramDto
import dev.rafael.contract.program.ScheduleEntry
import dev.rafael.contract.workout.WorkoutDto
import dev.rafael.contract.workout.WorkoutExerciseDto
import dev.rafael.contract.workout.WorkoutSetDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

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
        split = SplitType.UPPER_LOWER,
        focusMuscles = listOf(MuscleGroup.CHEST, MuscleGroup.BACK),
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
    fun `dia trancado mapeia locked e usa lockedExerciseCount (ARCH 23)`() {
        val dto = ProgramDto(
            id = "p", name = "P", daysPerWeek = 3, split = SplitType.PUSH_PULL_LEGS,
            workouts = listOf(
                WorkoutDto(id = "w1", name = "Dia 1", exercises = listOf(exDto(0), exDto(1))),
                WorkoutDto(id = "w2", name = "Dia 2", exercises = emptyList(), locked = true, lockedExerciseCount = 6),
            ),
        )
        val d = dto.toDomain()
        assertFalse(d.workouts[0].locked)
        assertEquals(2, d.workouts[0].exerciseCount)
        assertTrue(d.workouts[1].locked)
        assertEquals(6, d.workouts[1].exerciseCount)   // contador vem do lockedExerciseCount, não do exercises vazio
    }

    @Test
    fun `campos do programa passam direto`() {
        val d = sampleDto().toDomain()
        assertEquals("prog-1", d.id)
        assertEquals("Meu programa", d.name)
        assertEquals(4, d.daysPerWeek)
        // Domain guarda a CHAVE do enum (String), não o `SplitType` tipado -- ver KDoc de
        // `Program.split` (:domain não depende de shared-contract).
        assertEquals("UPPER_LOWER", d.split)
        assertEquals(true, d.locked)
    }

    @Test
    fun `focusMuscles mapeia pras chaves do enum`() {
        val d = sampleDto().toDomain()
        assertEquals(listOf("CHEST", "BACK"), d.focusMuscles)
    }

    @Test
    fun `split nulo (programa manual) mapeia pra null`() {
        val d = ProgramDto(name = "Manual", daysPerWeek = 0, split = null).toDomain()
        assertEquals(null, d.split)
        assertEquals(emptyList(), d.focusMuscles)
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
            name = "Vazio", daysPerWeek = 0,
        ).toDomain()
        assertEquals(0, d.workouts.size)
        assertEquals(0, d.schedule.size)
    }

    @Test
    fun `isActive do workout passa direto (o treino de hoje, derivado no servidor)`() {
        val dto = ProgramDto(
            id = "p", name = "P", daysPerWeek = 2,
            workouts = listOf(
                WorkoutDto(id = "w1", name = "Ativo", isActive = true),
                WorkoutDto(id = "w2", name = "Outro", isActive = false),
            ),
        )
        val d = dto.toDomain()
        assertTrue(d.workouts[0].isActive)
        assertFalse(d.workouts[1].isActive)
    }

    @Test
    fun `isActive do programa passa direto (V60, reverte a V59 -- ponteiro e do programa)`() {
        val ativo = ProgramDto(id = "p1", name = "Ativo", daysPerWeek = 2, isActive = true).toDomain()
        val inativo = ProgramDto(id = "p2", name = "Outro", daysPerWeek = 2, isActive = false).toDomain()
        assertTrue(ativo.isActive)
        assertFalse(inativo.isActive)
    }
}
