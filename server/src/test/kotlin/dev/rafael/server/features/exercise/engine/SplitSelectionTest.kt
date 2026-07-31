package dev.rafael.server.features.exercise.engine

import dev.rafael.contract.profile.Goal
import dev.rafael.contract.profile.Level
import dev.rafael.contract.profile.SplitCatalog
import dev.rafael.contract.profile.SplitType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Testa a escolha de split (ARCH #29): o motor respeita a preferência quando é válida
 * pro nº de dias; senão cai no recomendado (#26). E o catálogo curado por dias.
 */
class SplitSelectionTest {

    private val engine = StructureEngine()

    private fun sk(days: Int, split: SplitType? = null) =
        engine.buildSkeleton(Goal.GAIN_MUSCLE, Level.INTERMEDIATE, days, emptySet(), split)

    @Test
    fun `null usa o recomendado do numero de dias`() {
        assertEquals("Full Body", sk(2).split)
        assertEquals("Full Body", sk(3).split)
        assertEquals("Upper/Lower", sk(4).split)
        assertEquals("Upper/Lower + PPL", sk(5).split)
        assertEquals("Push/Pull/Legs", sk(6).split)
    }

    @Test
    fun `escolha valida e respeitada (Arnold em 6 dias)`() {
        val s = sk(6, SplitType.ARNOLD)
        assertEquals("Arnold", s.split)
        assertEquals(6, s.days.size)
        val labels = s.days.map { it.label }
        assertTrue(labels.any { it.startsWith("Peito+Costas") })
        assertTrue(labels.any { it.startsWith("Pernas") })
        assertTrue(labels.any { it.startsWith("Ombros+Braços") })
    }

    @Test
    fun `escolha invalida pro numero de dias cai no recomendado`() {
        // Arnold não existe em 3 dias → volta pro recomendado (Full Body).
        assertEquals("Full Body", sk(3, SplitType.ARNOLD).split)
    }

    @Test
    fun `3 dias com Upper-Lower-Full monta os tres tipos`() {
        val labels = sk(3, SplitType.UPPER_LOWER_FULL).days.map { it.label }
        assertEquals(listOf("Upper", "Lower", "Full Body"), labels)
    }

    @Test
    fun `catalogo lista opcoes por dias com recomendado`() {
        val opts3 = SplitCatalog.optionsFor(3)
        assertEquals(SplitType.FULL_BODY, opts3.first { it.recommended }.type)
        assertEquals(
            setOf(SplitType.FULL_BODY, SplitType.UPPER_LOWER_FULL, SplitType.PUSH_PULL_LEGS),
            opts3.map { it.type }.toSet(),
        )
    }

    @Test
    fun `catalogo valida e resolve`() {
        assertTrue(SplitCatalog.isValid(6, SplitType.ARNOLD))
        assertFalse(SplitCatalog.isValid(3, SplitType.ARNOLD))
        assertEquals(SplitType.PUSH_PULL_LEGS, SplitCatalog.recommendedFor(6))
        assertEquals(SplitType.ARNOLD, SplitCatalog.resolve(6, SplitType.ARNOLD))
        assertEquals(SplitType.FULL_BODY, SplitCatalog.resolve(3, SplitType.ARNOLD)) // inválido → recomendado
    }
}
