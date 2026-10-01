package dev.rafael.features.wiki.presentation.leitura

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DataDoArtigoTest {

    @Test
    fun `quebra o iso nos tres numeros`() {
        assertEquals(PartesDaData(dia = 15, mes = 6, ano = 2026), partesDaData("2026-06-15"))
    }

    @Test
    fun `zero a esquerda nao vira outro numero`() {
        assertEquals(1, partesDaData("2026-01-09")!!.mes)
        assertEquals(9, partesDaData("2026-01-09")!!.dia)
    }

    // --- caminhos de falha (C2): dado torto omite a data, não derruba o artigo ---

    @Test
    fun `formato errado devolve null`() {
        assertNull(partesDaData("15/06/2026"))
    }

    @Test
    fun `mes fora da faixa devolve null`() {
        assertNull(partesDaData("2026-13-01"))
    }

    @Test
    fun `texto vazio devolve null`() {
        assertNull(partesDaData("   "))
    }
}
