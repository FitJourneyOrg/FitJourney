package dev.rafael.server.features.exercise.engine.v3

import dev.rafael.contract.profile.Goal
import dev.rafael.contract.workout.Papel
import dev.rafael.server.features.exercise.engine.v3.SugestaoDeProgressao.Sugestao
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Fatia "sugestao-de-progressao" (2026-11-06). Tier 1: só a função pura, sem repositório — a
 * leitura do `ProgressaoRepository`/`ExerciseRepository` e a amarração na rota ficam pro Tier 2.
 */
class SugestaoDeProgressaoTest {

    @Test
    fun `papel nulo -- treino manual sem prescricao -- devolve vazia (caminho de falha)`() {
        val sugestao = SugestaoDeProgressao.calcular(
            incremento = 10.0,
            equipment = "BARBELL",
            papel = null,
            goal = Goal.GAIN_MUSCLE,
            estadoCarga = Progressao.Estado(carga = 50.0),
            estadoRepeticao = null,
        )
        assertEquals(Sugestao.VAZIA, sugestao)
    }

    @Test
    fun `eixo carga com estado calibrado devolve a carga atual`() {
        val sugestao = SugestaoDeProgressao.calcular(
            incremento = 2.5,
            equipment = "BARBELL",
            papel = Papel.COMPOSTO,
            goal = Goal.GAIN_MUSCLE,
            estadoCarga = Progressao.Estado(carga = 60.0),
            estadoRepeticao = null,
        )
        assertEquals(Sugestao(cargaKg = 60.0), sugestao)
    }

    @Test
    fun `eixo carga sem estado -- calibracao pendente -- devolve vazia (caminho de falha)`() {
        val sugestao = SugestaoDeProgressao.calcular(
            incremento = 2.5,
            equipment = "BARBELL",
            papel = Papel.COMPOSTO,
            goal = Goal.GAIN_MUSCLE,
            estadoCarga = null,
            estadoRepeticao = null,
        )
        assertEquals(Sugestao.VAZIA, sugestao)
    }

    @Test
    fun `eixo carga com estado mas carga nula -- calibracao pendente -- devolve vazia`() {
        val sugestao = SugestaoDeProgressao.calcular(
            incremento = 2.5,
            equipment = "BARBELL",
            papel = Papel.COMPOSTO,
            goal = Goal.GAIN_MUSCLE,
            estadoCarga = Progressao.Estado(carga = null),
            estadoRepeticao = null,
        )
        assertEquals(Sugestao.VAZIA, sugestao)
    }

    @Test
    fun `eixo repeticao com estado existente devolve a faixa atual, nao a original`() {
        val sugestao = SugestaoDeProgressao.calcular(
            incremento = null,
            equipment = "BODYWEIGHT",
            papel = Papel.ISOLAMENTO,
            goal = Goal.GAIN_MUSCLE,
            estadoCarga = null,
            estadoRepeticao = ProgressaoPorRepeticao.Estado(repsMin = 13, repsMax = 17, exposicoes = 1),
        )
        assertEquals(Sugestao(repsMin = 13, repsMax = 17), sugestao)
    }

    @Test
    fun `eixo repeticao sem estado -- 1a exposicao -- cai na faixa original da prescricao`() {
        // Isolamento, GAIN_MUSCLE: PrescricaoV3.de -> Faixa(12, 16, 60, 1).
        val sugestao = SugestaoDeProgressao.calcular(
            incremento = null,
            equipment = "BODYWEIGHT",
            papel = Papel.ISOLAMENTO,
            goal = Goal.GAIN_MUSCLE,
            estadoCarga = null,
            estadoRepeticao = null,
        )
        assertEquals(Sugestao(repsMin = 12, repsMax = 16), sugestao)
    }

    @Test
    fun `incremento nulo e equipment nao-bodyweight -- BAND -- devolve vazia (caminho de falha)`() {
        val sugestao = SugestaoDeProgressao.calcular(
            incremento = null,
            equipment = "BAND",
            papel = Papel.ACESSORIO,
            goal = Goal.GAIN_MUSCLE,
            estadoCarga = null,
            estadoRepeticao = null,
        )
        assertEquals(Sugestao.VAZIA, sugestao)
    }
}
