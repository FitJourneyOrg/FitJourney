package dev.rafael.server.features.exercise.engine.v3

import dev.rafael.contract.profile.Accessory
import dev.rafael.contract.profile.Goal
import dev.rafael.contract.profile.Level
import dev.rafael.contract.profile.TrainingEnvironment
import dev.rafael.server.features.exercise.engine.TargetMuscle
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Motor v3, Fatia 6 — fim a fim: `MotorV3Pipeline.gerar()` contra `tools/motor_sim/motor_v3.py
 * ::gerar()`, com `mv.CATALOGO` substituído pelo mesmo catálogo pequeno (1 composto + 1
 * isolamento por músculo) usado aqui. Não reprova cada peça (Fatias 1–6 já têm seus próprios
 * testes) — prova que a MONTAGEM (ordem das chamadas, tipos, o ponto fixo por cima) bate com o
 * oráculo byte a byte: `alocado`, `entregue`, `compostosSemana`, `indireto`, `disparos` e
 * `iteracoes`.
 */
class MotorV3PipelineTest {

    private fun catalogoUmDeCadaPorMusculo() = TargetMuscle.entries.flatMap { m ->
        listOf(
            exercicioV3("$m composto", m, composto = true, familia = "${m}_composto", ambiente = dev.rafael.server.features.exercise.models.Ambiente.AMBOS),
            exercicioV3("$m isolamento", m, composto = false, familia = "${m}_isolamento", ambiente = dev.rafael.server.features.exercise.models.Ambiente.AMBOS),
        )
    }

    @Test
    fun `fim a fim contra o oraculo - 4 dias, intermediario, ganho de massa`() {
        val entrada = MotorV3Pipeline.Entrada(
            objetivo = Goal.GAIN_MUSCLE, nivel = Level.INTERMEDIATE, dias = 4, minutos = 60,
            idade = 30, mesocicloAtual = null, ambiente = TrainingEnvironment.ACADEMIA,
            acessorios = Accessory.entries.toSet(),
        )
        val resultado = MotorV3Pipeline.gerar(entrada, catalogoUmDeCadaPorMusculo())

        assertEquals("UPPER_LOWER", resultado.divisao)
        assertEquals(65.33333333333333, resultado.capacidade, absoluteTolerance = 1e-9)
        assertEquals(4, resultado.iteracoes)
        assertEquals(
            mapOf(
                TargetMuscle.BACK to 13, TargetMuscle.BICEPS to 3, TargetMuscle.CALVES to 6,
                TargetMuscle.CHEST to 11, TargetMuscle.FOREARMS to 3, TargetMuscle.GLUTES to 2,
                TargetMuscle.POSTERIOR to 8, TargetMuscle.QUADS to 9, TargetMuscle.SHOULDERS to 9,
                TargetMuscle.TRICEPS to 1,
            ),
            resultado.estado.alocado,
        )
        assertEquals(
            mapOf(
                TargetMuscle.BACK to 13, TargetMuscle.BICEPS to 3, TargetMuscle.CALVES to 6,
                TargetMuscle.CHEST to 11, TargetMuscle.FOREARMS to 3, TargetMuscle.GLUTES to 2,
                TargetMuscle.POSTERIOR to 8, TargetMuscle.QUADS to 9, TargetMuscle.SHOULDERS to 9,
                TargetMuscle.TRICEPS to 2,
            ),
            resultado.estado.entregue,
        )
        assertEquals(
            mapOf(
                TargetMuscle.BACK to 8, TargetMuscle.CHEST to 7, TargetMuscle.POSTERIOR to 8,
                TargetMuscle.QUADS to 7, TargetMuscle.SHOULDERS to 7,
            ),
            resultado.estado.compostosSemana,
        )
        assertEquals(
            mapOf(
                TargetMuscle.BACK to 0.0, TargetMuscle.BICEPS to 4.0, TargetMuscle.CALVES to 0.0,
                TargetMuscle.CHEST to 0.0, TargetMuscle.FOREARMS to 0.0,
                TargetMuscle.GLUTES to 7.5, TargetMuscle.POSTERIOR to 0.0, TargetMuscle.QUADS to 0.0,
                TargetMuscle.SHOULDERS to 0.0, TargetMuscle.TRICEPS to 7.0,
            ),
            resultado.indireto,
        )
        // PROVISÓRIO — medido em 2026-10-27, ANTES DO PISO DIRETO. Reproduzido no oráculo com
        // este mesmo catálogo sintético (`equipamento = MACHINE`, o default de `exercicioV3`).
        // O antebraço aparece em `alocado`/`entregue` pela primeira vez (3 séries): os dois pares
        // `→ antebraço` saíram da tabela de indireto por falta de fonte, então o alvo dele virou
        // demanda direta de verdade.
        assertEquals(mapOf("escassez" to 1, "sobe_ao_piso_do_isolamento" to 1), resultado.disparos)
        assertEquals(emptyList(), resultado.estado.vagasVazias)
    }
}

private fun assertEquals(expected: Double, actual: Double, absoluteTolerance: Double) {
    kotlin.test.assertTrue(kotlin.math.abs(expected - actual) <= absoluteTolerance, "esperado $expected, obtido $actual")
}
