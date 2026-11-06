package dev.rafael.server.features.exercise.engine.v3

import dev.rafael.contract.profile.Goal
import dev.rafael.contract.workout.Papel
import dev.rafael.server.features.exercise.engine.v3.ProgressaoPorRepeticao.Decisao
import dev.rafael.server.features.exercise.engine.v3.ProgressaoPorRepeticao.Estado
import dev.rafael.server.features.exercise.engine.v3.ProgressaoPorRepeticao.Serie
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Progressão por repetição — fatia "progressao-por-repeticao" (2026-11-06,
 * `docs/trabalho/progressao-por-repeticao-plano.md`). Paralelo a [ProgressaoTest]: mesmos
 * critérios de aceite (topo sobe, abaixo desce, piso na faixa original), eixo de reps em vez de
 * carga. Cada bloco tem ao menos um caminho de falha.
 */
class ProgressaoPorRepeticaoTest {

    // Isolamento, GAIN_MUSCLE: Faixa(12, 16, 60, 1) — ver PrescricaoV3.
    private val faixaIsolamento = PrescricaoV3.de(Goal.GAIN_MUSCLE, Papel.ISOLAMENTO)
    private val estadoInicial = Estado(repsMin = 12, repsMax = 16, exposicoes = 0)

    private fun sessao(reps: Int, rir: Int?, n: Int = 3) = List(n) { Serie(reps, rir) }

    @Test
    fun `bate o topo da faixa atual com RIR no alvo sobe repsMin e repsMax em 1`() {
        val (novo, decisao) = ProgressaoPorRepeticao.decidir(estadoInicial, sessao(16, 1), faixaIsolamento)
        assertEquals(Decisao.SOBE, decisao)
        assertEquals(Estado(13, 17, 1, 0), novo)
    }

    @Test
    fun `RIR nulo nao bloqueia o topo -- a regra que precisa dele so nao age`() {
        val (novo, decisao) = ProgressaoPorRepeticao.decidir(estadoInicial, sessao(16, null), faixaIsolamento)
        assertEquals(Decisao.SOBE, decisao)
        assertEquals(17, novo.repsMax)
    }

    @Test
    fun `alguma serie abaixo do minimo desce repsMin e repsMax em 1`() {
        // estado já subiu uma vez (13-17); uma série abaixo do mínimo atual (13) devolve 1
        val subiu = Estado(repsMin = 13, repsMax = 17, exposicoes = 1, semSubir = 0)
        val series = listOf(Serie(12, 1), Serie(15, 1), Serie(15, 1))
        val (novo, decisao) = ProgressaoPorRepeticao.decidir(subiu, series, faixaIsolamento)
        assertEquals(Decisao.DESCE, decisao)
        assertEquals(Estado(12, 16, 2, 1), novo)
    }

    @Test
    fun `descida nunca passa do piso da faixa original`() {
        // já na própria faixa original (12-16): uma série abaixo do mínimo não pode cair mais que isso
        val series = listOf(Serie(10, 1), Serie(14, 1), Serie(14, 1))
        val (novo, _) = ProgressaoPorRepeticao.decidir(estadoInicial, series, faixaIsolamento)
        assertEquals(12, novo.repsMin)
        assertEquals(16, novo.repsMax)
    }

    @Test
    fun `entre o minimo e o topo da faixa atual mantem e conta pro semSubir`() {
        val (novo, decisao) = ProgressaoPorRepeticao.decidir(estadoInicial, sessao(14, 1), faixaIsolamento)
        assertEquals(Decisao.MANTEM, decisao)
        assertEquals(estadoInicial.copy(exposicoes = 1, semSubir = 1), novo)
    }

    @Test
    fun `no teto de 20 repeticoes bater o topo so mantem, nao sobe mais`() {
        val noTeto = Estado(repsMin = 16, repsMax = 20, exposicoes = 9, semSubir = 0)
        val (novo, decisao) = ProgressaoPorRepeticao.decidir(noTeto, sessao(20, 2), faixaIsolamento)
        assertEquals(Decisao.MANTEM, decisao)
        assertEquals(20, novo.repsMax)
        assertEquals(1, novo.semSubir)
    }

    @Test
    fun `sessao sem series falha alto`() {
        assertFailsWith<IllegalArgumentException> {
            ProgressaoPorRepeticao.decidir(estadoInicial, emptyList(), faixaIsolamento)
        }
    }
}
