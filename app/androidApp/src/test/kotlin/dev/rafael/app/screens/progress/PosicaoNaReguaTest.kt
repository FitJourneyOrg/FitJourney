package dev.rafael.app.screens.progress

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * O eixo X do grafico de 1RM (J.4.3b).
 *
 * ## O defeito que isto trava
 *
 * O x saia do indice do ponto entre os pontos QUE EXISTEM: oito pontos viravam 0, 1/7 … 1 e a
 * linha preenchia a largura do cartao independente da janela. Com 8 semanas era inofensivo,
 * porque dado e janela coincidiam. Com 52 a linha desenhava um ano de progressao onde havia dois
 * meses de dado — e, como sempre nesta tela, o desenho continuava convincente.
 */
class PosicaoNaReguaTest {

    private val janelaDe4 = listOf("2026-09-07", "2026-09-14", "2026-09-21", "2026-09-28")

    @Test
    fun `as pontas sao 0 e 1, e o meio e proporcional`() {
        assertEquals(0f, posicaoNaRegua("2026-09-07", janelaDe4))
        assertEquals(1f / 3, posicaoNaRegua("2026-09-14", janelaDe4))
        assertEquals(1f, posicaoNaRegua("2026-09-28", janelaDe4))
    }

    /**
     * ⭐ O caso que motivou a fatia: dado so no fim de uma janela longa.
     *
     * Antes, dois pontos viravam 0 e 1 e a linha atravessava o cartao. Agora ela ocupa o ultimo
     * terco, que e onde o treino aconteceu.
     */
    @Test
    fun `dado concentrado no fim da janela nao se espalha pela largura`() {
        val primeiro = posicaoNaRegua("2026-09-21", janelaDe4)!!
        assertEquals(2f / 3, primeiro)
        assertEquals(1f, posicaoNaRegua("2026-09-28", janelaDe4))
    }

    /** Caminho de falha: semana fora da janela e descartada, nao encaixada numa ponta. */
    @Test
    fun `semana fora da regua devolve nulo`() {
        assertNull(posicaoNaRegua("2026-01-05", janelaDe4))
    }

    /**
     * Regua de um elemento devolve 0, e nao `NaN`.
     *
     * Dividir por `size - 1` daria zero no denominador, e o Canvas desenha `NaN` como NADA — o
     * grafico sumiria sem erro, log ou crash. E o tipo de defeito que so aparece em producao, na
     * primeira semana de quem acabou de comecar.
     */
    @Test
    fun `regua de uma semana so nao divide por zero`() {
        assertEquals(0f, posicaoNaRegua("2026-09-28", listOf("2026-09-28")))
    }

    @Test
    fun `regua vazia devolve nulo, e nao zero`() {
        // Zero desenharia o ponto na esquerda como se houvesse eixo. Nao ha.
        assertNull(posicaoNaRegua("2026-09-28", emptyList()))
    }
}
