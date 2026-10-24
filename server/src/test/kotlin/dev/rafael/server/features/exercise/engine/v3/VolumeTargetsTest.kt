package dev.rafael.server.features.exercise.engine.v3

import dev.rafael.contract.profile.Goal
import dev.rafael.contract.profile.Level
import dev.rafael.server.features.exercise.engine.TargetMuscle
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Motor v3, Fatia 1 (`docs/trabalho/motor-v3-fatia-1.md`) — prova que a tabela de volume e a de
 * indireto reproduzem os números da especificação (`propostas/motor-v3-ESPECIFICACAO.md`):
 * os totais 61·111·148 por nível, e a demanda direta 86,0·113,0 (intermediário/avançado).
 */
class VolumeTargetsTest {

    @Test
    fun `alvo semanal soma 61 no iniciante`() {
        assertEquals(61, totalAlvo(Level.BEGINNER, Goal.GAIN_MUSCLE))
    }

    @Test
    fun `alvo semanal soma 111 no intermediario`() {
        assertEquals(111, totalAlvo(Level.INTERMEDIATE, Goal.GAIN_MUSCLE))
    }

    @Test
    fun `alvo semanal soma 148 no avancado`() {
        assertEquals(148, totalAlvo(Level.ADVANCED, Goal.GAIN_MUSCLE))
    }

    @Test
    fun `braco sobe a 12 no intermediario, nao fica em 3`() {
        assertEquals(12, VolumeTargets.weeklySets(TargetMuscle.BICEPS, Level.INTERMEDIATE, Goal.GAIN_MUSCLE))
        assertEquals(12, VolumeTargets.weeklySets(TargetMuscle.TRICEPS, Level.INTERMEDIATE, Goal.GAIN_MUSCLE))
    }

    @Test
    fun `abdomen fica em zero em todo nivel, catalogo nao tem exercicio`() {
        Level.entries.forEach { nivel ->
            assertEquals(0, VolumeTargets.weeklySets(TargetMuscle.CORE, nivel, Goal.GAIN_MUSCLE))
        }
        assertEquals(null, VolumeTargets.mrv(TargetMuscle.CORE))
    }

    @Test
    fun `saude geral usa o piso mesmo no avancado`() {
        assertEquals(
            VolumeTargets.weeklySets(TargetMuscle.BACK, Level.BEGINNER, Goal.GAIN_MUSCLE),
            VolumeTargets.weeklySets(TargetMuscle.BACK, Level.ADVANCED, Goal.GENERAL_HEALTH),
        )
    }

    @Test
    fun `demanda direta do intermediario reproduz a tabela da especificacao`() {
        val resultado = demandaDiretaPorMusculo(ALVO_INTERMEDIARIO, COMPOSTO_INTERMEDIARIO)

        assertEquals(14.0, resultado.getValue(TargetMuscle.CHEST))
        assertEquals(16.0, resultado.getValue(TargetMuscle.BACK))
        assertEquals(12.0, resultado.getValue(TargetMuscle.SHOULDERS))
        assertEquals(8.0, resultado.getValue(TargetMuscle.BICEPS))
        assertEquals(4.0, resultado.getValue(TargetMuscle.TRICEPS))
        assertEquals(0.0, resultado.getValue(TargetMuscle.FOREARMS))
        assertEquals(12.0, resultado.getValue(TargetMuscle.QUADS))
        assertEquals(10.0, resultado.getValue(TargetMuscle.POSTERIOR))
        assertEquals(9.0, resultado.getValue(TargetMuscle.CALVES))
        assertEquals(1.0, resultado.getValue(TargetMuscle.GLUTES))
        assertEquals(0.0, resultado.getValue(TargetMuscle.CORE))
        assertEquals(86.0, resultado.values.sum())
    }

    @Test
    fun `demanda direta do avancado reproduz a tabela da especificacao`() {
        val resultado = demandaDiretaPorMusculo(ALVO_AVANCADO, COMPOSTO_AVANCADO)

        assertEquals(18.0, resultado.getValue(TargetMuscle.CHEST))
        assertEquals(20.0, resultado.getValue(TargetMuscle.BACK))
        assertEquals(16.0, resultado.getValue(TargetMuscle.SHOULDERS))
        assertEquals(7.0, resultado.getValue(TargetMuscle.BICEPS))
        assertEquals(6.0, resultado.getValue(TargetMuscle.TRICEPS))
        assertEquals(0.0, resultado.getValue(TargetMuscle.FOREARMS))
        assertEquals(16.0, resultado.getValue(TargetMuscle.QUADS))
        assertEquals(14.0, resultado.getValue(TargetMuscle.POSTERIOR))
        assertEquals(12.0, resultado.getValue(TargetMuscle.CALVES))
        assertEquals(4.0, resultado.getValue(TargetMuscle.GLUTES))
        assertEquals(0.0, resultado.getValue(TargetMuscle.CORE))
        assertEquals(113.0, resultado.values.sum())
    }

    @Test
    fun `ombro nao e descontado por nenhum doador`() {
        // Supino dá trabalho real ao deltoide anterior, mas descontar subserviria lateral e
        // posterior — por isso SHOULDERS nunca aparece como recipiente.
        val indireto = VolumeIndirectTable.indiretoRecebido(COMPOSTO_INTERMEDIARIO)
        assertEquals(null, indireto[TargetMuscle.SHOULDERS])
    }

    @Test
    fun `gluteo credita 0,2 ao posterior, nao 0,5`() {
        val indireto = VolumeIndirectTable.indiretoRecebido(mapOf(TargetMuscle.GLUTES to 10.0))
        assertEquals(2.0, indireto.getValue(TargetMuscle.POSTERIOR))
    }

    @Test
    fun `demanda direta nunca fica negativa quando indireto excede o alvo`() {
        // Antebraço no intermediário: alvo 6, indireto 7 — é o caso real da especificação.
        assertEquals(0.0, demandaDireta(alvo = 6.0, indireto = 7.0))
    }

    private fun totalAlvo(nivel: Level, objetivo: Goal): Int =
        TargetMuscle.entries.sumOf { VolumeTargets.weeklySets(it, nivel, objetivo) }

    companion object {
        // Alvos semanais (especificação, tabela "Volume semanal por músculo").
        private val ALVO_INTERMEDIARIO = mapOf(
            TargetMuscle.CHEST to 14.0, TargetMuscle.BACK to 16.0, TargetMuscle.SHOULDERS to 12.0,
            TargetMuscle.BICEPS to 12.0, TargetMuscle.TRICEPS to 12.0, TargetMuscle.FOREARMS to 6.0,
            TargetMuscle.QUADS to 12.0, TargetMuscle.POSTERIOR to 10.0, TargetMuscle.CALVES to 9.0,
            TargetMuscle.GLUTES to 8.0, TargetMuscle.CORE to 0.0,
        )
        private val ALVO_AVANCADO = mapOf(
            TargetMuscle.CHEST to 18.0, TargetMuscle.BACK to 20.0, TargetMuscle.SHOULDERS to 16.0,
            TargetMuscle.BICEPS to 15.0, TargetMuscle.TRICEPS to 16.0, TargetMuscle.FOREARMS to 9.0,
            TargetMuscle.QUADS to 16.0, TargetMuscle.POSTERIOR to 14.0, TargetMuscle.CALVES to 12.0,
            TargetMuscle.GLUTES to 12.0, TargetMuscle.CORE to 0.0,
        )

        // Séries de composto por semana de cada doador (especificação, "Como a demanda direta
        // foi recalculada", passo 1 — produzido pela regra de composição, Fatia 5).
        private val COMPOSTO_INTERMEDIARIO = mapOf(
            TargetMuscle.CHEST to 8.0, TargetMuscle.BACK to 8.0, TargetMuscle.SHOULDERS to 8.0,
            TargetMuscle.QUADS to 8.0, TargetMuscle.POSTERIOR to 6.0, TargetMuscle.GLUTES to 0.0,
        )
        private val COMPOSTO_AVANCADO = mapOf(
            TargetMuscle.CHEST to 14.0, TargetMuscle.BACK to 16.0, TargetMuscle.SHOULDERS to 6.0,
            TargetMuscle.QUADS to 8.0, TargetMuscle.POSTERIOR to 8.0, TargetMuscle.GLUTES to 0.0,
        )
    }
}
