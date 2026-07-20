package dev.rafael.server.features.exercise.engine

import dev.rafael.contract.profile.Goal
import dev.rafael.contract.profile.Level
import dev.rafael.contract.profile.MuscleGroup
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse

/**
 * Testa o motor de estrutura (F.2) — puro, sem banco nem Exercise.
 * Prova as decisões travadas: split por dias, foco só INTER/ADV, descanso por nível.
 */
class StructureEngineTest {

    private val engine = StructureEngine()

    @Test
    fun `split deriva dos dias`() {
        assertEquals("Full Body", skeleton(days = 2).split)
        assertEquals("Full Body", skeleton(days = 3).split)
        assertEquals("Upper/Lower", skeleton(days = 4).split)
        assertEquals("Upper/Lower + PPL", skeleton(days = 5).split)
        assertEquals("Push/Pull/Legs", skeleton(days = 6).split)
    }

    @Test
    fun `numero de dias gerados bate com daysPerWeek`() {
        assertEquals(2, skeleton(days = 2).days.size)
        assertEquals(4, skeleton(days = 4).days.size)
        assertEquals(6, skeleton(days = 6).days.size)
    }

    @Test
    fun `dias fora de 2 a 6 sao normalizados`() {
        assertEquals(2, skeleton(days = 1).days.size)  // coerceIn -> 2
        assertEquals(6, skeleton(days = 9).days.size)  // coerceIn -> 6
    }

    @Test
    fun `descanso escala com o nivel`() {
        val beg = firstSlotRest(Level.BEGINNER)
        val int = firstSlotRest(Level.INTERMEDIATE)
        val adv = firstSlotRest(Level.ADVANCED)
        assertTrue(beg < int, "iniciante descansa menos que intermediario")
        assertTrue(int < adv, "intermediario descansa menos que avancado")
    }

    @Test
    fun `foco adiciona slots para INTERMEDIATE`() {
        val semFoco = engine.buildSkeleton(Goal.GAIN_MUSCLE, Level.INTERMEDIATE, 4, emptySet())
        val comFoco = engine.buildSkeleton(Goal.GAIN_MUSCLE, Level.INTERMEDIATE, 4, setOf(MuscleGroup.ARMS))
        val slotsSemFoco = semFoco.days.sumOf { it.slots.size }
        val slotsComFoco = comFoco.days.sumOf { it.slots.size }
        assertTrue(slotsComFoco > slotsSemFoco, "foco deve adicionar slots")
        // e os slots extras sao marcados como foco
        assertTrue(comFoco.days.any { d -> d.slots.any { it.isFocus } })
    }

    @Test
    fun `rationale menciona o foco quando presente`() {
        val comFoco = engine.buildSkeleton(Goal.GAIN_MUSCLE, Level.ADVANCED, 5, setOf(MuscleGroup.ARMS))
        assertTrue(comFoco.rationale.contains("ARMS"), "rationale deve explicar o foco")
    }

    // helpers
    private fun skeleton(days: Int) =
        engine.buildSkeleton(Goal.GAIN_MUSCLE, Level.INTERMEDIATE, days, emptySet())

    private fun firstSlotRest(level: Level): Int =
        engine.buildSkeleton(Goal.GAIN_MUSCLE, level, 3, emptySet())
            .days.first().slots.first().restSeconds
}