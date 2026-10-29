package dev.rafael.server.features.exercise.engine.v3

import dev.rafael.server.features.exercise.engine.v3.AncoraDoIniciante.Evento
import dev.rafael.server.features.exercise.engine.v3.AncoraDoIniciante.Situacao
import dev.rafael.server.features.exercise.engine.v3.Progressao.Decisao
import dev.rafael.server.features.exercise.engine.v3.Progressao.Estado
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Motor v3, Fatia 10 — a troca por estagnação do iniciante (exceção NOMEADA à fixidez das âncoras) e
 * o retorno após pausa em três faixas.
 *
 * **Pausa não é dimensão da matriz** (é evento no tempo): fica registrada como inalcançável na
 * varredura dos 720 e coberta aqui, com histórico construído — mesmo padrão do `ombro_vira_isolamento`.
 */
class AncoraEPausaTest {

    private val S = Evento.Carga(Decisao.SOBE)
    private val M = Evento.Carga(Decisao.MANTEM)
    private val D = Evento.Carga(Decisao.DESCE)
    private val L = Evento.Deload

    private fun sit(vararg e: Evento, diagnostico: Boolean = false) = AncoraDoIniciante.situacao(e.toList(), diagnostico)

    // ------------------------------------------------------------------------------ âncora

    @Test
    fun `enquanto sobe a carga esta progredindo, e tres sessoes sem subir pedem o deload`() {
        assertEquals(Situacao.PROGREDINDO, sit())
        assertEquals(Situacao.PROGREDINDO, sit(S, M, M))
        assertEquals(Situacao.DELOAD, sit(S, M, M, M))
        assertEquals(Situacao.PROGREDINDO, sit(M, M, M, S), "subir de novo zera a contagem")
    }

    @Test
    fun `depois do deload espera tres sessoes, e subir em qualquer uma delas retoma`() {
        assertEquals(Situacao.AGUARDANDO_RETOMADA, sit(S, M, M, M, L))
        assertEquals(Situacao.AGUARDANDO_RETOMADA, sit(L, M, M))
        assertEquals(Situacao.RETOMOU, sit(L, M, S))
        assertEquals(Situacao.RETOMOU, sit(L, S, M, M))
    }

    @Test
    fun `a troca exige as tres condicoes - deload aplicado, estagnacao persistente e diagnostico`() {
        // as três valem
        assertEquals(Situacao.TROCA_AUTORIZADA, sit(M, M, M, L, M, M, M, diagnostico = true))
        // falta o diagnóstico: pede o diagnóstico, não troca
        assertEquals(Situacao.DIAGNOSTICO_PENDENTE, sit(M, M, M, L, M, M, M, diagnostico = false))
        // falta o deload: com o diagnóstico feito e muita estagnação, quem manda é o DELOAD
        assertEquals(Situacao.DELOAD, sit(M, M, M, M, M, M, diagnostico = true))
        // falta a persistência: ainda não passaram três sessões depois do deload
        assertEquals(Situacao.AGUARDANDO_RETOMADA, sit(M, M, M, L, M, M, diagnostico = true))
    }

    @Test
    fun `so o ultimo deload conta e subir depois dele afasta a troca`() {
        assertEquals(Situacao.AGUARDANDO_RETOMADA, sit(M, M, M, L, M, M, M, L, M, diagnostico = true))
        assertEquals(Situacao.RETOMOU, sit(L, M, M, S, diagnostico = true))
        // estagnar DE NOVO depois de retomar não é troca: volta a pedir deload (sinal de promoção, Fatia 11)
        assertEquals(Situacao.DELOAD, sit(L, S, M, M, M, diagnostico = true))
    }

    @Test
    fun `invariante independente - TROCA_AUTORIZADA so sai com deload e tres sessoes sem subir depois dele`() {
        // Varre TODOS os históricos de até 7 eventos e confere a propriedade sem usar a máquina.
        val alfabeto = listOf(S, M, D, L)
        var autorizadas = 0
        fun gera(prefixo: List<Evento>) {
            if (prefixo.isNotEmpty()) {
                val s = AncoraDoIniciante.situacao(prefixo, diagnosticoFeito = true)
                val i = prefixo.indexOfLast { it == L }
                val depois = if (i < 0) emptyList() else prefixo.drop(i + 1)
                val devia = i >= 0 && depois.size >= AncoraDoIniciante.RETOMADA_SESSOES && depois.none { it == S }
                assertEquals(devia, s == Situacao.TROCA_AUTORIZADA, "histórico $prefixo -> $s")
                if (s == Situacao.TROCA_AUTORIZADA) autorizadas++
            }
            if (prefixo.size < 7) alfabeto.forEach { gera(prefixo + it) }
        }
        gera(emptyList())
        assertTrue(autorizadas > 0, "a varredura nunca autorizou a troca: o invariante não foi exercitado")
        // sem o diagnóstico, nenhum histórico autoriza
        fun semDiagnostico(prefixo: List<Evento>) {
            if (prefixo.isNotEmpty()) {
                assertTrue(AncoraDoIniciante.situacao(prefixo, false) != Situacao.TROCA_AUTORIZADA, "histórico $prefixo")
            }
            if (prefixo.size < 7) alfabeto.forEach { semDiagnostico(prefixo + it) }
        }
        semDiagnostico(emptyList())
    }

    // ------------------------------------------------------------------------------ pausa

    @Test
    fun `ate 14 dias segue como estava`() {
        for (dias in listOf(0, 1, 14)) {
            assertEquals(RetornoAposPausa.Retorno(Mesociclo(2, 3), false, 1.0), RetornoAposPausa.apos(dias, Mesociclo(2, 3)), "dias=$dias")
        }
    }

    @Test
    fun `de 15 a 28 dias volta a semana 1 do mesmo mesociclo com a carga menor`() {
        for (dias in listOf(15, 21, 28)) {
            val r = RetornoAposPausa.apos(dias, Mesociclo(2, 3))
            assertEquals(Mesociclo(2, 1), r.mesociclo, "dias=$dias")
            assertEquals(false to 0.9, r.recalibra to r.fatorCarga, "dias=$dias")
        }
    }

    @Test
    fun `acima de 28 dias abre mesociclo novo e a recalibracao e obrigatoria`() {
        for (dias in listOf(29, 60, 400)) {
            val r = RetornoAposPausa.apos(dias, Mesociclo(2, 3))
            assertEquals(Mesociclo(3, 1), r.mesociclo, "dias=$dias")
            assertTrue(r.recalibra, "dias=$dias")
        }
    }

    @Test
    fun `dias negativos falham alto, porque o relogio e do servidor e nao anda para tras`() {
        assertFailsWith<IllegalArgumentException> { RetornoAposPausa.apos(-1, Mesociclo()) }
    }

    @Test
    fun `aplicar reduz a carga ao incremento, nunca sobe e nunca fica abaixo de um incremento`() {
        val r = RetornoAposPausa.apos(20, Mesociclo())
        assertEquals(90.0, RetornoAposPausa.aplicar(Estado(carga = 100.0, exposicoes = 9, posSalto = true), r, 2.5).carga)
        assertEquals(false, RetornoAposPausa.aplicar(Estado(carga = 100.0, exposicoes = 9, posSalto = true), r, 2.5).posSalto)
        assertEquals(2.5, RetornoAposPausa.aplicar(Estado(carga = 2.5, exposicoes = 9), r, 2.5).carga, "um incremento é o piso")
        assertNull(RetornoAposPausa.aplicar(Estado(carga = null), r, 2.5).carga, "quem não tem carga não ganha uma")
    }

    @Test
    fun `ate 14 dias o estado passa intacto e acima de 28 so o 1RM historico fica`() {
        val estado = Estado(carga = 80.0, exposicoes = 7, posSalto = true, umRm = 110.0, semSubir = 2)
        assertEquals(estado, RetornoAposPausa.aplicar(estado, RetornoAposPausa.apos(10, Mesociclo()), 2.5))
        val recal = RetornoAposPausa.aplicar(estado, RetornoAposPausa.apos(40, Mesociclo()), 2.5)
        assertEquals(Estado(carga = null, exposicoes = 0, posSalto = false, umRm = 110.0, semSubir = 0), recal)
    }

    // ------------------------------------------------------------------------------ 60+ que volta

    @Test
    fun `so o 60 mais com pausa longa abre o mesociclo conservador`() {
        assertTrue(RetornoAposPausa.apos(29, Mesociclo(3, 2), idade = 65).mesociclo.conservador)
        assertEquals(false, RetornoAposPausa.apos(29, Mesociclo(3, 2), idade = 59).mesociclo.conservador)
        assertEquals(false, RetornoAposPausa.apos(29, Mesociclo(3, 2)).mesociclo.conservador, "sem idade: não presume")
        for (dias in listOf(0, 14, 15, 28)) {
            assertEquals(false, RetornoAposPausa.apos(dias, Mesociclo(3, 2), idade = 65).mesociclo.conservador, "dias=$dias")
        }
    }

    @Test
    fun `a flag dura o mesociclo e o seguinte a apaga, e a pausa media a preserva`() {
        var m = RetornoAposPausa.apos(40, Mesociclo(3, 2), idade = 65).mesociclo
        repeat(3) { m = CicloDoMesociclo.concluirSemana(m); assertTrue(m.conservador) }
        m = CicloDoMesociclo.concluirSemana(m)
        assertEquals(Mesociclo(5, 1, false), m)
        assertTrue(RetornoAposPausa.apos(20, Mesociclo(4, 3, conservador = true), idade = 65).mesociclo.conservador)
    }
}
