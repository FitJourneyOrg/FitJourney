package dev.rafael.server.features.exercise.engine

import dev.rafael.contract.profile.Goal
import dev.rafael.contract.profile.Level
import dev.rafael.contract.profile.MuscleGroup
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Testa o motor de estrutura (F.2, reescrito no ARCH #27) — puro, sem banco.
 * Prova: split por dias, volume por nível, perna fina, piso de 3 séries, RIR, descanso por papel.
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
    fun `numero de dias bate com daysPerWeek`() {
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
    fun `series por exercicio ficam entre 3 e 5`() {
        skeleton(days = 5).days.forEach { d ->
            d.slots.forEach { assertTrue(it.sets in 3..5, "séries por exercício em [3,5], veio ${it.sets}") }
        }
    }

    @Test
    fun `nenhuma sessao passa do teto de 6 exercicios`() {
        listOf(2, 3, 4, 5, 6).forEach { days ->
            skeleton(days).days.forEach { d ->
                assertTrue(d.slots.size <= 6, "dia '${d.label}' ($days dias) tem ${d.slots.size} exercícios > 6")
            }
        }
    }

    @Test
    fun `todo slot tem RIR valido`() {
        skeleton(days = 4).days.forEach { d ->
            d.slots.forEach { assertTrue(it.rir in 0..4, "RIR prescrito por papel") }
        }
    }

    @Test
    fun `perna fina cobre quadriceps posterior e panturrilha`() {
        // 4 dias: index 1 = Lower A
        val lower = engine.buildSkeleton(Goal.GAIN_MUSCLE, Level.INTERMEDIATE, 4, emptySet()).days[1]
        val targets = lower.slots.map { it.target }.toSet()
        assertTrue(TargetMuscle.QUADS in targets, "dia de perna cobre quadríceps")
        assertTrue(TargetMuscle.POSTERIOR in targets, "dia de perna cobre posterior")
        assertTrue(TargetMuscle.CALVES in targets, "dia de perna cobre panturrilha")
    }

    @Test
    fun `volume escala com o nivel`() {
        val beg = totalSets(Level.BEGINNER)
        val adv = totalSets(Level.ADVANCED)
        assertTrue(adv > beg, "avançado tem mais volume total que iniciante ($adv > $beg)")
    }

    @Test
    fun `composto pesado vem primeiro e descansa mais que isolamento`() {
        val upper = engine.buildSkeleton(Goal.GAIN_MUSCLE, Level.INTERMEDIATE, 4, emptySet()).days[0]
        assertEquals(SlotRole.COMPOSTO_PESADO, upper.slots.first().role, "composto pesado abre o dia")
        assertTrue(
            upper.slots.first().restSeconds >= upper.slots.last().restSeconds,
            "composto pesado descansa >= isolamento",
        )
    }

    @Test
    fun `rationale menciona o foco quando presente`() {
        val comFoco = engine.buildSkeleton(Goal.GAIN_MUSCLE, Level.ADVANCED, 5, setOf(MuscleGroup.ARMS))
        assertTrue(comFoco.rationale.contains("ARMS"), "rationale deve explicar o foco")
    }

    // helpers
    private fun skeleton(days: Int) =
        engine.buildSkeleton(Goal.GAIN_MUSCLE, Level.INTERMEDIATE, days, emptySet())

    private fun totalSets(level: Level): Int =
        engine.buildSkeleton(Goal.GAIN_MUSCLE, level, 5, emptySet())
            .days.sumOf { d -> d.slots.sumOf { it.sets } }
}
