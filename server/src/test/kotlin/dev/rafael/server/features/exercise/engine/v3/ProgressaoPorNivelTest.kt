package dev.rafael.server.features.exercise.engine.v3

import dev.rafael.contract.profile.Goal
import dev.rafael.contract.workout.Papel
import dev.rafael.server.features.exercise.engine.v3.ProgressaoPorNivel.Decisao
import dev.rafael.server.features.exercise.engine.v3.ProgressaoPorNivel.Estado
import dev.rafael.server.features.exercise.engine.v3.ProgressaoPorNivel.Serie
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Progressão por nível — fatia "progressao-por-nivel" (2026-11-06,
 * `docs/trabalho/progressao-por-nivel-plano.md`). Paralelo a [ProgressaoPorRepeticaoTest]:
 * diferença-chave é que a FAIXA fica fixa (não sobe/desce) -- é o nível que muda. Cada bloco tem
 * ao menos um caminho de falha.
 */
class ProgressaoPorNivelTest {

    // Isolamento, GAIN_MUSCLE: Faixa(12, 16, 60, 1) — ver PrescricaoV3.
    private val faixaIsolamento = PrescricaoV3.de(Goal.GAIN_MUSCLE, Papel.ISOLAMENTO)
    private val estadoInicial = Estado()

    private fun sessao(reps: Int, rir: Int?, n: Int = 3) = List(n) { Serie(reps, rir) }

    @Test
    fun `bate o topo da faixa fixa com RIR no alvo sobe de nivel`() {
        val (novo, decisao) = ProgressaoPorNivel.decidir(estadoInicial, sessao(16, 1), faixaIsolamento)
        assertEquals(Decisao.SOBE, decisao)
        assertEquals(Estado(nivelAtual = 2, exposicoes = 1, semSubir = 0), novo)
    }

    @Test
    fun `RIR nulo nao bloqueia o topo -- a regra que precisa dele so nao age`() {
        val (novo, decisao) = ProgressaoPorNivel.decidir(estadoInicial, sessao(16, null), faixaIsolamento)
        assertEquals(Decisao.SOBE, decisao)
        assertEquals(2, novo.nivelAtual)
    }

    @Test
    fun `alguma serie abaixo do minimo da faixa fixa desce de nivel`() {
        val noNivel3 = Estado(nivelAtual = 3, exposicoes = 2, semSubir = 0)
        val series = listOf(Serie(10, 1), Serie(14, 1), Serie(14, 1))
        val (novo, decisao) = ProgressaoPorNivel.decidir(noNivel3, series, faixaIsolamento)
        assertEquals(Decisao.DESCE, decisao)
        assertEquals(Estado(nivelAtual = 2, exposicoes = 3, semSubir = 1), novo)
    }

    @Test
    fun `desce nunca passa do nivel 1`() {
        val series = listOf(Serie(10, 1), Serie(14, 1), Serie(14, 1))
        val (novo, decisao) = ProgressaoPorNivel.decidir(estadoInicial, series, faixaIsolamento)
        assertEquals(Decisao.DESCE, decisao)
        assertEquals(1, novo.nivelAtual, "já começava no nível 1 -- não existe nível 0")
    }

    @Test
    fun `entre o minimo e o topo da faixa fixa mantem e conta pro semSubir`() {
        val (novo, decisao) = ProgressaoPorNivel.decidir(estadoInicial, sessao(14, 1), faixaIsolamento)
        assertEquals(Decisao.MANTEM, decisao)
        assertEquals(estadoInicial.copy(exposicoes = 1, semSubir = 1), novo)
    }

    @Test
    fun `1a exposicao ja comeca no nivel 1, sem calibracao`() {
        assertEquals(1, estadoInicial.nivelAtual)
    }

    @Test
    fun `sessao sem series falha alto`() {
        assertFailsWith<IllegalArgumentException> {
            ProgressaoPorNivel.decidir(estadoInicial, emptyList(), faixaIsolamento)
        }
    }
}
