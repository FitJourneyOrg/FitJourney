package dev.rafael.features.wiki.presentation.leitura

import kotlin.test.Test
import kotlin.test.assertEquals

class TempoDeLeituraTest {

    @Test
    fun `arredonda para cima`() {
        // 201 palavras a 200 p/min não são "1 minuto e um tiquinho": são 2 na tela.
        assertEquals(2, minutosDeLeitura(corpo = "palavra ".repeat(201)))
    }

    @Test
    fun `texto curto tem no minimo um minuto`() {
        // "0 min de leitura" pareceria defeito, não concisão.
        assertEquals(1, minutosDeLeitura(corpo = "tres palavras aqui"))
    }

    @Test
    fun `corpo vazio nao devolve zero`() {
        assertEquals(1, minutosDeLeitura(corpo = "   "))
    }

    @Test
    fun `quebras de linha nao contam como palavra`() {
        assertEquals(1, minutosDeLeitura(corpo = "uma\n\nduas\n   tres"))
    }
}
