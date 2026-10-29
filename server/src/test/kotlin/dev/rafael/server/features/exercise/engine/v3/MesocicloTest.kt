package dev.rafael.server.features.exercise.engine.v3

import dev.rafael.contract.profile.Goal
import dev.rafael.contract.profile.Level
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Motor v3, Fatia 10 — o ciclo de 4 semanas, o deload, a CONTINUIDADE do programa e o fechamento do
 * débito do 60+. Espelha `ContinuidadeDoPrograma` e
 * `MesocicloAtualRealTiraO60MaisDoConservadorPermanente` de `test_progressao_v3.py`.
 */
class MesocicloTest {

    // ------------------------------------------------------------------------------ o ciclo

    @Test
    fun `o mesociclo avanca por conclusao e a semana 4 abre o seguinte`() {
        var m = Mesociclo()
        val vistos = mutableListOf<Pair<Int, Int>>()
        repeat(9) { vistos += m.numero to m.semana; m = CicloDoMesociclo.concluirSemana(m) }
        assertEquals(
            listOf(1 to 1, 1 to 2, 1 to 3, 1 to 4, 2 to 1, 2 to 2, 2 to 3, 2 to 4, 3 to 1),
            vistos,
        )
    }

    @Test
    fun `deload na semana 4 para intermediario e avancado, nunca por calendario para o iniciante`() {
        for (nivel in listOf(Level.INTERMEDIATE, Level.ADVANCED)) {
            assertEquals(listOf(false, false, false, true), (1..4).map { CicloDoMesociclo.deloadProgramado(nivel, Mesociclo(1, it)) })
        }
        // caminho de falha: o iniciante NUNCA cai no deload de calendário (25% do tempo dele seria desperdício)
        assertTrue((1..4).none { CicloDoMesociclo.deloadProgramado(Level.BEGINNER, Mesociclo(1, it)) })
    }

    @Test
    fun `o deload corta as series pela metade, meio pra cima, e fora dele nada muda`() {
        assertEquals(listOf(1, 1, 2, 2, 3, 3), (1..6).map { CicloDoMesociclo.seriesDoDia(it, true) })
        assertEquals(listOf(1, 2, 3, 4, 5, 6), (1..6).map { CicloDoMesociclo.seriesDoDia(it, false) })
    }

    // ------------------------------------------------------------------------------ continuidade

    private val catalogo = Oraculo.catalogo()
    private val entrada = Oraculo.entradaDe("GAIN_MUSCLE-INTERMEDIATE-4d-60m-30a-ACADEMIA")

    private fun programa() = MotorV3Pipeline.gerar(entrada, catalogo).estado.sessoes.map { (dia, vagas) -> dia to vagas }

    @Test
    fun `o exercicio de cada posicao e o mesmo em todas as semanas de dois mesociclos`() {
        val sessoes = programa()
        var m = Mesociclo()
        val exercicios = mutableListOf<List<List<String>>>()
        repeat(8) {
            exercicios += CicloDoMesociclo.semanaDoPrograma(sessoes, Level.INTERMEDIATE, m).map { (_, vagas) -> vagas.map { it.exercicio } }
            m = CicloDoMesociclo.concluirSemana(m)
        }
        assertTrue(exercicios.first().flatten().isNotEmpty(), "o programa de teste saiu vazio")
        assertEquals(1, exercicios.toSet().size, "o exercício de alguma posição mudou entre semanas")
    }

    @Test
    fun `no deload so as series mudam, e as vagas vazias nao sao prescritas`() {
        val sessoes = programa()
        val normal = CicloDoMesociclo.semanaDoPrograma(sessoes, Level.INTERMEDIATE, Mesociclo(1, 1))
        val deload = CicloDoMesociclo.semanaDoPrograma(sessoes, Level.INTERMEDIATE, Mesociclo(1, 4))
        assertEquals(normal.map { (_, v) -> v.map { it.exercicio } }, deload.map { (_, v) -> v.map { it.exercicio } })
        assertEquals(normal.map { (_, v) -> v.map { it.alvo to it.papel } }, deload.map { (_, v) -> v.map { it.alvo to it.papel } })
        assertTrue(deload.sumOf { (_, v) -> v.sumOf { it.series } } < normal.sumOf { (_, v) -> v.sumOf { it.series } })
        assertTrue(deload.flatMap { it.second }.zip(normal.flatMap { it.second }).all { (d, n) -> d.series == CicloDoMesociclo.seriesDoDia(n.series, true) })

        // caminho de falha: uma vaga sem exercício some da semana em vez de virar prescrição vazia
        val vazia = sessoes.mapIndexed { i, (dia, vagas) -> dia to if (i == 0) listOf(vagas.first().copy(exercicio = null)) + vagas.drop(1) else vagas }
        val semVaga = CicloDoMesociclo.semanaDoPrograma(vazia, Level.INTERMEDIATE, Mesociclo(1, 1))
        assertEquals(normal.first().second.size - 1, semVaga.first().second.size)
    }

    @Test
    fun `o iniciante so tem deload quando a estagnacao pede, e a semana 4 dele e igual as outras`() {
        val sessoes = programa()
        val s1 = CicloDoMesociclo.semanaDoPrograma(sessoes, Level.BEGINNER, Mesociclo(1, 1))
        val s4 = CicloDoMesociclo.semanaDoPrograma(sessoes, Level.BEGINNER, Mesociclo(1, 4))
        assertEquals(s1, s4)
        val extra = CicloDoMesociclo.semanaDoPrograma(sessoes, Level.BEGINNER, Mesociclo(1, 2), deloadExtra = true)
        assertTrue(extra.sumOf { (_, v) -> v.sumOf { it.series } } < s1.sumOf { (_, v) -> v.sumOf { it.series } })
    }

    // ------------------------------------------------------------------------------ débito do 60+

    @Test
    fun `fecha o debito - o 60 mais sai do modo conservador depois de um mesociclo concluido`() {
        val sem = CicloDoMesociclo.paraPerfil(null)
        var m = Mesociclo()
        repeat(CicloDoMesociclo.SEMANAS) { m = CicloDoMesociclo.concluirSemana(m) }
        val depois = CicloDoMesociclo.paraPerfil(m)
        assertEquals(1 to 2, sem to depois)

        // sem estado: lado seguro (igual ao `null` de antes); com o mesociclo real: sai do conservador
        assertTrue(SafetyRules.modoConservadorAutomatico(Level.INTERMEDIATE, Goal.GAIN_MUSCLE, 65, sem))
        assertFalse(SafetyRules.modoConservadorAutomatico(Level.INTERMEDIATE, Goal.GAIN_MUSCLE, 65, depois))
        // e no meio do 1º mesociclo ele ainda é conservador
        assertTrue(SafetyRules.modoConservadorAutomatico(Level.INTERMEDIATE, Goal.GAIN_MUSCLE, 65, CicloDoMesociclo.paraPerfil(Mesociclo(1, 3))))
    }

    @Test
    fun `fecha o debito - o teto de sessao do 60 mais volta ao do adulto, e o iniciante segue conservador`() {
        fun teto(idade: Int, nivel: Level, mesociclo: Int?) = CapacityModel.tetoDaSessao(
            minutos = 60, dias = 4, nivel = nivel, goal = Goal.GAIN_MUSCLE, idade = idade, mesocicloAtual = mesociclo,
        )
        assertEquals(0.75 * 18, teto(65, Level.INTERMEDIATE, CicloDoMesociclo.paraPerfil(null)), 1e-9)
        assertEquals(teto(30, Level.INTERMEDIATE, 2), teto(65, Level.INTERMEDIATE, 2), 1e-9)
        assertTrue(teto(65, Level.INTERMEDIATE, 1) < teto(65, Level.INTERMEDIATE, 2))
        // caminho de falha: iniciante 60+ NÃO sai do conservador com o mesociclo
        assertEquals(0.75 * 16, teto(65, Level.BEGINNER, 5), 1e-9)
        assertTrue(SafetyRules.modoConservadorAutomatico(Level.BEGINNER, Goal.GAIN_MUSCLE, 65, 5))
        // quem tem menos de 60 não muda com o mesociclo
        assertEquals(teto(59, Level.INTERMEDIATE, 1), teto(59, Level.INTERMEDIATE, 2), 1e-9)
    }

    @Test
    fun `fecha o debito - o pipeline entrega mais capacidade ao 60 mais no segundo mesociclo`() {
        val base = Oraculo.entradaDe("GAIN_MUSCLE-INTERMEDIATE-4d-60m-65a-ACADEMIA")
        val primeiro = MotorV3Pipeline.gerar(base.copy(mesocicloAtual = CicloDoMesociclo.paraPerfil(null)), catalogo)
        val segundo = MotorV3Pipeline.gerar(base.copy(mesocicloAtual = 2), catalogo)
        assertTrue(segundo.capacidade > primeiro.capacidade, "${primeiro.capacidade} -> ${segundo.capacidade}")
    }

    @Test
    fun `60 mais que volta depois de 28 dias fica conservador mesmo no mesociclo 4`() {
        val m = RetornoAposPausa.apos(40, Mesociclo(3, 2), idade = 65).mesociclo
        assertEquals(4, CicloDoMesociclo.paraPerfil(m))
        val cons = CicloDoMesociclo.retornoConservadorParaPerfil(m)
        assertTrue(cons)
        assertTrue(SafetyRules.modoConservadorAutomatico(Level.INTERMEDIATE, Goal.GAIN_MUSCLE, 65, 4, cons))
        // contraprova: sem o retorno, o mesmo 60+ no mesociclo 4 NÃO é conservador
        assertFalse(SafetyRules.modoConservadorAutomatico(Level.INTERMEDIATE, Goal.GAIN_MUSCLE, 65, 4))
        // a flag só vale com 60+
        assertFalse(SafetyRules.modoConservadorAutomatico(Level.INTERMEDIATE, Goal.GAIN_MUSCLE, 40, 4, true))
        fun teto(idade: Int, c: Boolean) = CapacityModel.tetoDaSessao(
            minutos = 60, dias = 4, nivel = Level.INTERMEDIATE, goal = Goal.GAIN_MUSCLE, idade = idade, mesocicloAtual = 4, retornoConservador = c,
        )
        assertEquals(0.75 * 18, teto(65, true), 1e-9)
        // o teto por tempo (60 min, 4 dias = 16,33) é o que manda sem a flag; compara-se com o adulto
        assertEquals(teto(40, false), teto(65, false), 1e-9)
        assertEquals(teto(40, false), teto(40, true), 1e-9)
        assertTrue(teto(65, true) < teto(65, false))
        assertFalse(CicloDoMesociclo.retornoConservadorParaPerfil(null))
    }

    @Test
    fun `o pipeline entrega menos capacidade ao 60 mais que volta de uma pausa longa`() {
        val base = Oraculo.entradaDe("GAIN_MUSCLE-INTERMEDIATE-4d-60m-65a-ACADEMIA").copy(mesocicloAtual = 4)
        val normal = MotorV3Pipeline.gerar(base, catalogo)
        val voltou = MotorV3Pipeline.gerar(base.copy(retornoConservador = true), catalogo)
        assertTrue(voltou.capacidade < normal.capacidade, "${normal.capacidade} -> ${voltou.capacidade}")
        assertEquals(1, voltou.disparos["modo_conservador_ativo"])
    }
}
