package dev.rafael.server.features.exercise.engine.v3

import dev.rafael.contract.profile.Goal
import dev.rafael.contract.profile.Level
import dev.rafael.contract.profile.TrainingEnvironment
import dev.rafael.server.features.exercise.engine.TargetMuscle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Motor v3, Fatia 7 — espelha `server/src/test/oracle/motor_v3.py::modo_conservador_automatico()`
 *  e `rir_minimo()`. Cada gatilho/piso tem caso positivo e caminho de falha (C2). */
class SafetyRulesTest {

    // ---------------------------------------------------------------- modoConservadorAutomatico

    @Test
    fun `dispara para iniciante`() {
        assertTrue(
            SafetyRules.modoConservadorAutomatico(Level.BEGINNER, Goal.GAIN_MUSCLE, idade = 30, mesocicloAtual = 3),
        )
    }

    @Test
    fun `dispara para saude geral`() {
        assertTrue(
            SafetyRules.modoConservadorAutomatico(Level.INTERMEDIATE, Goal.GENERAL_HEALTH, idade = 30, mesocicloAtual = 3),
        )
    }

    @Test
    fun `dispara para 60 mais no 1o mesociclo, inclusive mesociclo nulo`() {
        assertTrue(
            SafetyRules.modoConservadorAutomatico(Level.INTERMEDIATE, Goal.GAIN_MUSCLE, idade = 65, mesocicloAtual = null),
        )
        assertTrue(
            SafetyRules.modoConservadorAutomatico(Level.ADVANCED, Goal.GAIN_MUSCLE, idade = 65, mesocicloAtual = 1),
        )
    }

    @Test
    fun `nao dispara para 60 mais fora do 1o mesociclo, nem para intermediario comum (caminho de falha)`() {
        assertFalse(
            SafetyRules.modoConservadorAutomatico(Level.ADVANCED, Goal.GAIN_MUSCLE, idade = 65, mesocicloAtual = 2),
        )
        assertFalse(
            SafetyRules.modoConservadorAutomatico(Level.INTERMEDIATE, Goal.GAIN_MUSCLE, idade = 30, mesocicloAtual = 3),
        )
    }

    // -------------------------------------------------------------------------------- rirMinimo

    @Test
    fun `modo conservador vale 3, mesmo em exercicio sem a propriedade`() {
        val exercicio = exercicioV3("Rosca Direta", TargetMuscle.BICEPS, composto = false, rirMinimoSeguranca = false)
        assertEquals(3, SafetyRules.rirMinimo(exercicio, modoConservador = true))
    }

    @Test
    fun `propriedade do exercicio vale 2 sem modo conservador`() {
        val exercicio = exercicioV3("Levantamento Terra", TargetMuscle.POSTERIOR, composto = true, rirMinimoSeguranca = true)
        assertEquals(2, SafetyRules.rirMinimo(exercicio, modoConservador = false))
    }

    @Test
    fun `sem modo conservador e sem a propriedade, nenhum piso (caminho de falha)`() {
        val exercicio = exercicioV3("Puxada na Polia", TargetMuscle.BACK, composto = true, rirMinimoSeguranca = false)
        assertNull(SafetyRules.rirMinimo(exercicio, modoConservador = false))
        // vaga vazia (sem exercicio escolhido) também não aplica piso nenhum
        assertNull(SafetyRules.rirMinimo(null, modoConservador = false))
    }

    // ------------------------------------------------------------- [INV]: BEGINNER nunca recebe
    // rirMinimoSeguranca=true -- propriedade de CatalogPool + curadoria (level), não do laço de
    // preenchimento. A troca de barra "para o iniciante" da especificação já está satisfeita
    // por isto, sem código (ver docs/regras/01-motor-de-geracao.md).

    @Test
    fun `INV - CatalogPool nunca deixa um BEGINNER ver exercicio com rirMinimoSeguranca`() {
        val catalogo = listOf(
            exercicioV3(
                "Levantamento Terra", TargetMuscle.POSTERIOR, composto = true,
                equipamento = "BARBELL", nivel = Level.INTERMEDIATE, rirMinimoSeguranca = true,
            ),
            exercicioV3(
                "Stiff com Halteres", TargetMuscle.POSTERIOR, composto = true,
                equipamento = "DUMBBELL", nivel = Level.BEGINNER, rirMinimoSeguranca = false,
            ),
        )
        val poolBeginner = CatalogPool.pool(
            catalogo, TrainingEnvironment.ACADEMIA, Level.BEGINNER, acessorios = emptySet(),
        )
        assertTrue(poolBeginner.none { it.rirMinimoSeguranca }, "BEGINNER não pode ver $poolBeginner")

        val poolIntermediate = CatalogPool.pool(
            catalogo, TrainingEnvironment.ACADEMIA, Level.INTERMEDIATE, acessorios = emptySet(),
        )
        assertTrue(poolIntermediate.any { it.rirMinimoSeguranca }, "INTERMEDIATE deveria ver o Levantamento Terra")
    }
}
