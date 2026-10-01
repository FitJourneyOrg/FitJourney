package dev.rafael.features.wiki.data

import dev.rafael.contract.wiki.WikiCategory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import dev.rafael.core.database.WikiArticle as WikiArticleRow

class WikiMapperTest {

    private fun linha(
        slug: String = "guia-do-iniciante",
        categoria: String = "TRAINING",
        destaque: Long = 0L,
        ordem: Long = 1L,
    ) = WikiArticleRow(
        id = "id-$slug",
        slug = slug,
        category = categoria,
        title = "Titulo",
        body = "Corpo",
        featured = destaque,
        orderIndex = ordem,
        updatedAt = "2026-06-15",
    )

    @Test
    fun `converte a linha do cache para o dominio`() {
        val artigo = linha(destaque = 1L, ordem = 3L).toDomainOrNull()!!

        assertEquals("guia-do-iniciante", artigo.slug)
        assertEquals(WikiCategory.TRAINING, artigo.category)
        assertTrue(artigo.featured, "featured é INTEGER no SQLite: 1 tem de virar true")
        assertEquals(3, artigo.orderIndex)
        assertEquals("2026-06-15", artigo.updatedAt)
    }

    @Test
    fun `featured zero vira false`() {
        assertEquals(false, linha(destaque = 0L).toDomainOrNull()!!.featured)
    }

    /**
     * Caminho de falha (C2): categoria que este app não conhece veio de um servidor mais novo.
     * Perder UM artigo é melhor que derrubar a lista inteira com um `valueOf` estourando.
     */
    @Test
    fun `categoria desconhecida devolve null em vez de estourar`() {
        assertNull(linha(categoria = "CIENTIFICO").toDomainOrNull())
    }

    @Test
    fun `o resto do acervo sobrevive a uma linha invalida`() {
        val linhas = listOf(
            linha(slug = "bom-1"),
            linha(slug = "ruim", categoria = "CIENTIFICO"),
            linha(slug = "bom-2"),
        )

        val artigos = linhas.mapNotNull { it.toDomainOrNull() }

        assertEquals(listOf("bom-1", "bom-2"), artigos.map { it.slug })
    }
}
