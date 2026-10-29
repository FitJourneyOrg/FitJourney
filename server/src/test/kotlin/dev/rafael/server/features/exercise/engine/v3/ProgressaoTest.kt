package dev.rafael.server.features.exercise.engine.v3

import dev.rafael.contract.profile.Goal
import dev.rafael.server.features.exercise.engine.v3.Progressao.Decisao
import dev.rafael.server.features.exercise.engine.v3.Progressao.Estado
import dev.rafael.server.features.exercise.engine.v3.Progressao.Serie
import dev.rafael.server.features.exercise.engine.v3.SessionComposition.Papel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Motor v3, Fatia 10 — progressão dupla, calibração e 1RM, com histórico FALSO escrito à mão. Cada
 * bloco tem ao menos um caminho de falha. Espelha `test_progressao_v3.py`; a grade contra o oráculo
 * está em [ProgressaoContraOOraculoTest].
 */
class ProgressaoTest {

    private fun sessao(reps: Int, rir: Int, n: Int = 3) = List(n) { Serie(reps, rir) }

    // ------------------------------------------------------------------------- arredondamento

    @Test
    fun `vai ao multiplo mais proximo e o empate desce`() {
        assertEquals(95.0, Progressao.paraIncremento(96.0, 2.5))
        assertEquals(92.5, Progressao.paraIncremento(92.0, 2.5))
        assertEquals(8.0, Progressao.paraIncremento(7.68, 2.0))
        // 56,25 está exatamente entre 55 e 57,5: a carga menor é a segura
        assertEquals(55.0, Progressao.paraIncremento(56.25, 2.5))
        assertEquals(2.0, Progressao.paraIncremento(3.0, 2.0))
    }

    @Test
    fun `incremento invalido falha alto`() {
        assertFailsWith<IllegalArgumentException> { Progressao.paraIncremento(10.0, 0.0) }
        assertFailsWith<IllegalArgumentException> { Progressao.paraIncremento(10.0, -2.5) }
    }

    @Test
    fun `as series arredondam meio pra cima`() {
        assertEquals(listOf(1, 2, 2, 3), listOf(0.5, 1.5, 1.9, 2.5).map { Progressao.arredondar(it) })
    }

    // ------------------------------------------------------------------------- salto da faixa

    @Test
    fun `as tres faixas da spec e o teto de 25 por cento em barra livre`() {
        assertEquals(listOf(0.10, 0.10, 0.25, 0.25, 0.50, 0.50), listOf(1, 2, 3, 4, 5, 9).map { Progressao.saltoDaFaixa(it, false) })
        assertEquals(listOf(0.10, 0.25, 0.25), listOf(2, 4, 7).map { Progressao.saltoDaFaixa(it, true) })
    }

    @Test
    fun `sem excesso de RIR nao ha salto`() {
        assertEquals(listOf(0.0, 0.0), listOf(0, -3).map { Progressao.saltoDaFaixa(it, false) })
    }

    // ------------------------------------------------------------------------- calibração

    @Test
    fun `20 kg viram 55 kg em tres series e 60 kg na terceira exposicao`() {
        // O caso da spec (l.80, l.600): quem calibra com 20 kg e aguenta 60 kg levava ~16 sessões
        // subindo 2,5 kg. Capacidade REAL = 60 kg (10 repetições deixando 2); o relato é
        // determinístico: RIR = 2 + 10 × (1 − carga/60), meio pra cima, no máximo 10.
        val capacidade = 60.0
        val alvo = 2
        val inc = 2.5
        fun rirSentido(carga: Double) = minOf(10, maxOf(0, Progressao.arredondar(alvo + 10 * (1 - carga / capacidade))))

        // exposição 1: calibração, até 3 séries (cada uma informa o RIR da carga em que foi feita)
        var carga = 20.0
        val historico = mutableListOf<Pair<Double, Int>>()
        for (n in 1..3) {
            val rir = rirSentido(carga)
            historico += carga to rir
            val passo = Progressao.passoCalibracao(carga, rir, n, alvo, false, inc)
            carga = passo.carga
            if (passo.terminou) break
        }
        assertEquals(listOf(20.0 to 9, 30.0 to 7, 45.0 to 5), historico)
        assertEquals(55.0, carga, "20 -> 30 (+50%) -> 45 (+50%) -> 55 (+25%, empate desce)")
        assertTrue(carga <= capacidade, "a calibração nunca passa da capacidade real")
        assertTrue(carga / capacidade >= 0.9)

        // exposição 2: a 55 kg a pessoa faz o topo da faixa com RIR 3 -> salto de 10% (1 acima do alvo)
        val (estado, decisao) = Progressao.decidir(
            Estado(carga = carga, exposicoes = 1), sessao(9, rirSentido(55.0)), Papel.COMPOSTO, Goal.GAIN_MUSCLE, inc, false,
        )
        assertEquals(Decisao.SOBE, decisao)
        assertEquals(60.0, estado.carga)
        assertEquals(2, estado.exposicoes)
        // a exposição 3 já COMEÇA em 60 kg = a capacidade. São três exposições, não 16 sessões.
    }

    @Test
    fun `o caminho antigo levava 16 sessoes`() {
        assertEquals(16, ((60.0 - 20.0) / 2.5).toInt())
    }

    @Test
    fun `barra livre nao dispara o salto de 50 por cento`() {
        assertEquals(25.0, Progressao.passoCalibracao(20.0, 9, 1, 2, true, 2.5).carga, "teto de +25% em barra livre")
        var carga = 20.0
        var ultimo = Progressao.PassoCalibracao(carga, false)
        for (n in 1..3) {
            ultimo = Progressao.passoCalibracao(carga, 9, n, 2, true, 2.5)
            carga = ultimo.carga
        }
        assertTrue(carga <= 20.0 * 1.25 * 1.25 * 1.25 + 1e-9)
        assertTrue(ultimo.terminou)
    }

    @Test
    fun `RIR abaixo de 4 termina sem mexer na carga`() {
        for (rir in listOf(0, 2, 3)) {
            val passo = Progressao.passoCalibracao(40.0, rir, 1, 2, false, 2.5)
            assertEquals(40.0, passo.carga, "rir $rir")
            assertTrue(passo.terminou)
        }
    }

    @Test
    fun `o salto nunca arredonda para a mesma carga`() {
        // 10% de 10 kg = 1 kg, num incremento de 2,5 kg: sem o piso a calibração travaria
        assertEquals(12.5, Progressao.cargaAposSalto(10.0, 0.10, 2.5))
    }

    @Test
    fun `a terceira serie ainda sobe e encerra`() {
        val passo = Progressao.passoCalibracao(45.0, 9, 3, 2, false, 2.5)
        assertTrue(passo.terminou)
        assertTrue(passo.carga > 45.0)
    }

    @Test
    fun `calibrar devolve a primeira exposicao e lista vazia falha alto`() {
        val e = Progressao.calibrar(20.0, listOf(9, 7, 5), 2, false, 2.5)
        assertEquals(Estado(carga = 55.0, exposicoes = 1), e)
        assertFailsWith<IllegalArgumentException> { Progressao.calibrar(20.0, emptyList(), 2, false, 2.5) }
    }

    // ------------------------------------------------------------------------- progressão dupla

    @Test
    fun `regra 1 - sobe so quando todas as series batem o topo no RIR alvo`() {
        val base = Estado(carga = 100.0, exposicoes = 10)
        // composto/ganhar massa: 6-9 reps, RIR 2
        val (sobe, d1) = Progressao.decidir(base, sessao(9, 2), Papel.COMPOSTO, Goal.GAIN_MUSCLE, 2.5, true)
        assertEquals(Decisao.SOBE to 102.5, d1 to sobe.carga)
        // caminho de falha 1: UMA série fora do topo e nada sobe
        val (e2, d2) = Progressao.decidir(base, listOf(Serie(9, 2), Serie(9, 2), Serie(8, 2)), Papel.COMPOSTO, Goal.GAIN_MUSCLE, 2.5, true)
        assertEquals(Decisao.MANTEM to 100.0, d2 to e2.carga)
        // caminho de falha 2: topo de repetições, mas mais perto da falha que o alvo (RIR 1 < 2)
        val (e3, d3) = Progressao.decidir(base, sessao(9, 1), Papel.COMPOSTO, Goal.GAIN_MUSCLE, 2.5, true)
        assertEquals(Decisao.MANTEM to 100.0, d3 to e3.carga)
    }

    @Test
    fun `regra 2 - o aumento e o menor incremento depois das tres primeiras exposicoes`() {
        // RIR 6 (4 acima do alvo) pediria +25% se fosse uma das três primeiras
        val (e4, d4) = Progressao.decidir(Estado(carga = 100.0, exposicoes = 3), sessao(9, 6), Papel.COMPOSTO, Goal.GAIN_MUSCLE, 2.5, false)
        assertEquals(Decisao.SOBE to 102.5, d4 to e4.carga, "a 4a exposição já não tem salto grande")
        val (e3, d3) = Progressao.decidir(Estado(carga = 100.0, exposicoes = 2), sessao(9, 6), Papel.COMPOSTO, Goal.GAIN_MUSCLE, 2.5, false)
        assertEquals(Triple(Decisao.SOBE, 125.0, 3), Triple(d3, e3.carga, e3.exposicoes), "a 3a exposição ainda aceita +25%")
    }

    @Test
    fun `regra 2 - o salto grande usa o pior RIR da sessao`() {
        val (e, _) = Progressao.decidir(
            Estado(carga = 100.0, exposicoes = 1), listOf(Serie(9, 6), Serie(9, 3), Serie(9, 6)),
            Papel.COMPOSTO, Goal.GAIN_MUSCLE, 2.5, false,
        )
        assertEquals(110.0, e.carga, "min RIR = 3, 1 acima do alvo -> +10%")
    }

    @Test
    fun `regra 3 - a tolerancia pos-salto evita o loop de subir e devolver`() {
        // O halter que pula de 8 para 10 kg (+25%): na faixa 8-12 a pessoa faz 6 reps no RIR alvo.
        // Sem a tolerância a carga voltava para 8 e o ciclo recomeçava.
        var e = Estado(carga = 8.0, exposicoes = 10)
        var d: Decisao
        Progressao.decidir(e, sessao(12, 1), Papel.ACESSORIO, Goal.GAIN_MUSCLE, 2.0, false).let { (n, dec) -> e = n; d = dec }
        assertEquals(Triple(Decisao.SOBE, 10.0, true), Triple(d, e.carga, e.posSalto))
        Progressao.decidir(e, sessao(6, 1), Papel.ACESSORIO, Goal.GAIN_MUSCLE, 2.0, false).let { (n, dec) -> e = n; d = dec }
        assertEquals(Triple(Decisao.MANTEM, 10.0, true), Triple(d, e.carga, e.posSalto), "abaixo da faixa, RIR no alvo: aceita")
        Progressao.decidir(e, sessao(7, 1), Papel.ACESSORIO, Goal.GAIN_MUSCLE, 2.0, false).let { (n, dec) -> e = n; d = dec }
        assertEquals(Decisao.MANTEM to 10.0, d to e.carga, "segue aceitando enquanto o RIR está no alvo")
        Progressao.decidir(e, sessao(8, 1), Papel.ACESSORIO, Goal.GAIN_MUSCLE, 2.0, false).let { (n, dec) -> e = n; d = dec }
        assertEquals(Triple(Decisao.MANTEM, 10.0, false), Triple(d, e.carga, e.posSalto), "entrou na faixa: a tolerância acaba")
    }

    @Test
    fun `regra 3 - caminhos de falha`() {
        val aposSalto = Estado(carga = 10.0, exposicoes = 10, posSalto = true)
        // RIR mais difícil que o alvo: a tolerância NÃO vale, devolve um incremento
        val (e1, d1) = Progressao.decidir(aposSalto, sessao(6, 0), Papel.ACESSORIO, Goal.GAIN_MUSCLE, 2.0, false)
        assertEquals(Triple(Decisao.DESCE, 8.0, false), Triple(d1, e1.carga, e1.posSalto))
        // sem salto recente, abaixo da faixa devolve ([PROPOSTA]: o "devolver" implícito da spec)
        val (e2, d2) = Progressao.decidir(Estado(carga = 10.0, exposicoes = 10), sessao(6, 1), Papel.ACESSORIO, Goal.GAIN_MUSCLE, 2.0, false)
        assertEquals(Decisao.DESCE to 8.0, d2 to e2.carga)
        // nunca desce abaixo de um incremento
        val (e3, _) = Progressao.decidir(Estado(carga = 2.0, exposicoes = 10), sessao(1, 0), Papel.ACESSORIO, Goal.GAIN_MUSCLE, 2.0, false)
        assertEquals(2.0, e3.carga)
    }

    @Test
    fun `sem subir conta ate a proxima subida`() {
        var e = Estado(carga = 50.0, exposicoes = 5)
        for (esperado in 1..3) {
            e = Progressao.decidir(e, sessao(7, 2), Papel.COMPOSTO, Goal.GAIN_MUSCLE, 2.5, false).first
            assertEquals(esperado, e.semSubir)
        }
        val (n, d) = Progressao.decidir(e, sessao(9, 2), Papel.COMPOSTO, Goal.GAIN_MUSCLE, 2.5, false)
        assertEquals(Decisao.SOBE to 0, d to n.semSubir)
    }

    @Test
    fun `entradas invalidas falham alto`() {
        assertFailsWith<IllegalArgumentException> {
            Progressao.decidir(Estado(carga = null), sessao(9, 2), Papel.COMPOSTO, Goal.GAIN_MUSCLE, 2.5, false)
        }
        assertFailsWith<IllegalArgumentException> {
            Progressao.decidir(Estado(carga = 50.0), emptyList(), Papel.COMPOSTO, Goal.GAIN_MUSCLE, 2.5, false)
        }
    }

    @Test
    fun `a prescricao vem da spec`() {
        assertEquals(PrescricaoV3.Faixa(6, 9, 120, 2), PrescricaoV3.de(Goal.GAIN_MUSCLE, Papel.COMPOSTO))
        assertEquals(PrescricaoV3.Faixa(12, 18, 75, 3), PrescricaoV3.de(Goal.GENERAL_HEALTH, Papel.ISOLAMENTO))
        Papel.entries.forEach {
            assertEquals(PrescricaoV3.de(Goal.GAIN_MUSCLE, it), PrescricaoV3.de(Goal.LOSE_FAT, it), "perder gordura é idêntico a ganhar massa (item 18)")
        }
    }

    // ------------------------------------------------------------------------- redução no dia

    @Test
    fun `quatro por cento por ponto de RIR abaixo do alvo`() {
        assertEquals(95.0 to false, Progressao.reducaoIntrasessao(100.0, 1, 2, 2.5)) // 96 -> 95
        assertEquals(92.5 to false, Progressao.reducaoIntrasessao(100.0, 0, 2, 2.5)) // 92 -> 92,5
    }

    @Test
    fun `no alvo ou mais facil nada muda`() {
        assertEquals(100.0 to false, Progressao.reducaoIntrasessao(100.0, 2, 2, 2.5))
        assertEquals(100.0 to false, Progressao.reducaoIntrasessao(100.0, 5, 2, 2.5))
    }

    @Test
    fun `quando o incremento nao existe a reducao e anulada e avisa`() {
        // Checklist: "arredondamento da redução de 4% quando o incremento não existe". 4% de 8 kg é
        // 0,32 kg, num halter de 2 kg: não há carga menor. A carga fica e a regra DIZ que não agiu.
        assertEquals(8.0 to true, Progressao.reducaoIntrasessao(8.0, 1, 2, 2.0))
    }

    @Test
    fun `a reducao nunca vai abaixo de um incremento`() {
        assertTrue(Progressao.reducaoIntrasessao(2.5, 0, 3, 2.5).first >= 2.5)
    }

    // ------------------------------------------------------------------------- 1RM

    @Test
    fun `epley com RIR`() {
        assertEquals(100.0 * (1 + 10.0 / 30), Progressao.umRmEpley(100.0, 8, 2), 1e-9)
    }

    @Test
    fun `composto usa a melhor serie e isolamento nao usa 1RM`() {
        val series = listOf(Serie(8, 2), Serie(6, 2), Serie(8, 3))
        assertEquals(100.0 * (1 + 11.0 / 30), assertNotNull(Progressao.estimarUmRm(100.0, series, Papel.COMPOSTO)), 1e-9)
        assertNull(Progressao.estimarUmRm(20.0, series, Papel.ISOLAMENTO), "regra 5: isolamento só tem progressão dupla")
        assertNull(Progressao.estimarUmRm(100.0, emptyList(), Papel.COMPOSTO))
    }

    @Test
    fun `o 1RM acompanha o exercicio e entra no estado`() {
        val (e, _) = Progressao.decidir(Estado(carga = 100.0, exposicoes = 9), sessao(8, 2), Papel.COMPOSTO, Goal.GAIN_MUSCLE, 2.5, true)
        assertEquals(100.0 * (1 + 10.0 / 30), assertNotNull(e.umRm), 1e-9)
        val (e2, _) = Progressao.decidir(Estado(carga = 20.0, exposicoes = 9), sessao(14, 1), Papel.ISOLAMENTO, Goal.GAIN_MUSCLE, 1.0, false)
        assertNull(e2.umRm)
        assertFalse(e2.posSalto && e2.semSubir > 0)
    }

    // ------------------------------------------------------------------------- RIR nulo (D-3)

    @Test
    fun `rir nulo aplica so as regras por repeticao - topo sobe um incremento, sem salto`() {
        // com RIR alto na 2ª exposição o salto seria +25%; sem RIR nada é inferido
        val (e, d) = Progressao.decidir(
            Estado(carga = 100.0, exposicoes = 1), List(3) { Serie(9, null) }, Papel.COMPOSTO, Goal.GAIN_MUSCLE, 2.5, false,
        )
        assertEquals(Decisao.SOBE to 102.5, d to e.carga)
    }

    @Test
    fun `uma serie sem rir nao impede o topo nem libera o salto`() {
        val mista = listOf(Serie(9, 6), Serie(9, null), Serie(9, 6))
        val (e, d) = Progressao.decidir(Estado(carga = 100.0, exposicoes = 1), mista, Papel.COMPOSTO, Goal.GAIN_MUSCLE, 2.5, false)
        assertEquals(Decisao.SOBE to 102.5, d to e.carga, "RIR faltando em UMA série: não há pior RIR para saltar")
    }

    @Test
    fun `abaixo da faixa sem rir devolve porque a tolerancia precisa do rir`() {
        val pos = Estado(carga = 10.0, exposicoes = 10, posSalto = true)
        val (e, d) = Progressao.decidir(pos, List(3) { Serie(6, null) }, Papel.ACESSORIO, Goal.GAIN_MUSCLE, 2.0, false)
        assertEquals(Triple(Decisao.DESCE, 8.0, false), Triple(d, e.carga, e.posSalto))
        // contraprova: com RIR no alvo a mesma sessão é tolerada
        val (e2, d2) = Progressao.decidir(pos, List(3) { Serie(6, 1) }, Papel.ACESSORIO, Goal.GAIN_MUSCLE, 2.0, false)
        assertEquals(Decisao.MANTEM to 10.0, d2 to e2.carga)
    }

    @Test
    fun `sem rir a reducao de 4 por cento nao age e nao se diz anulada`() {
        assertEquals(100.0 to false, Progressao.reducaoIntrasessao(100.0, null, 2, 2.5))
    }

    @Test
    fun `sem rir a calibracao nao mexe na carga e o 1RM historico sobrevive`() {
        val p = Progressao.passoCalibracao(20.0, null, 1, 2, false, 2.5)
        assertEquals(20.0 to true, p.carga to p.terminou)
        val (e, _) = Progressao.decidir(
            Estado(carga = 100.0, exposicoes = 9, umRm = 140.0), List(3) { Serie(8, null) }, Papel.COMPOSTO, Goal.GAIN_MUSCLE, 2.5, true,
        )
        assertEquals(140.0, e.umRm)
        val (e2, _) = Progressao.decidir(
            Estado(carga = 100.0, exposicoes = 9), listOf(Serie(8, null), Serie(8, 2), Serie(8, null)), Papel.COMPOSTO, Goal.GAIN_MUSCLE, 2.5, true,
        )
        assertEquals(100.0 * (1 + 10.0 / 30), assertNotNull(e2.umRm), 1e-9)
    }
}
