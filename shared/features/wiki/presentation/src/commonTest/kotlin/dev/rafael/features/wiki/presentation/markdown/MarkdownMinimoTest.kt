package dev.rafael.features.wiki.presentation.markdown

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MarkdownMinimoTest {

    @Test
    fun `paragrafos separados por linha em branco viram blocos`() {
        val blocos = blocosDoArtigo("Primeiro.\n\nSegundo.")

        assertEquals(2, blocos.size)
        assertTrue(blocos.all { it is Bloco.Paragrafo })
    }

    @Test
    fun `junta as linhas do mesmo paragrafo antes de procurar negrito`() {
        // Os .md são quebrados em ~100 colunas para o diff do git; a quebra é do arquivo, não do
        // texto. Sem juntar, o negrito que atravessa a quebra sairia com asterisco literal.
        val blocos = blocosDoArtigo("comece **em\nnegrito** e siga")

        val p = blocos.single() as Bloco.Paragrafo
        assertEquals(listOf("comece ", "em negrito", " e siga"), p.trechos.map { it.texto })
        assertEquals(listOf(false, true, false), p.trechos.map { it.negrito })
    }

    @Test
    fun `subtitulo com dois sustenidos`() {
        val blocos = blocosDoArtigo("## Tres formas de progredir")

        assertEquals(Bloco.Subtitulo("Tres formas de progredir"), blocos.single())
    }

    @Test
    fun `lista vira um item por linha`() {
        val blocos = blocosDoArtigo("- primeiro\n- segundo")

        assertEquals(2, blocos.size)
        assertTrue(blocos.all { it is Bloco.Item })
        assertEquals("primeiro", (blocos[0] as Bloco.Item).trechos.single().texto)
    }

    @Test
    fun `hifen no meio do paragrafo nao vira lista`() {
        // Só a PRIMEIRA linha abrindo com "- " faz o bloco virar lista.
        val blocos = blocosDoArtigo("uma frase\n- isto continua a frase")

        assertTrue(blocos.single() is Bloco.Paragrafo)
    }

    @Test
    fun `negrito no meio da linha vira tres trechos`() {
        val p = blocosDoArtigo("antes **meio** depois").single() as Bloco.Paragrafo

        assertEquals(3, p.trechos.size)
        assertEquals("meio", p.trechos[1].texto)
        assertTrue(p.trechos[1].negrito)
    }

    /** Caminho de falha (C2): marcação torta aparece levemente errada, nunca derruba o artigo. */
    @Test
    fun `asterisco sem par vira texto literal em vez de estourar`() {
        val p = blocosDoArtigo("isto abre **e nunca fecha").single() as Bloco.Paragrafo

        assertEquals("isto abre **e nunca fecha", p.trechos.single().texto)
        assertEquals(false, p.trechos.single().negrito)
    }

    @Test
    fun `corpo vazio devolve nenhum bloco em vez de um bloco vazio`() {
        assertEquals(emptyList(), blocosDoArtigo("   \n\n  "))
    }
}
