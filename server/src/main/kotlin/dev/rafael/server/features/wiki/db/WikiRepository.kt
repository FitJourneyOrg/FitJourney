package dev.rafael.server.features.wiki.db

import dev.rafael.contract.i18n.Idioma
import dev.rafael.core.result.AppResult
import dev.rafael.server.features.wiki.models.WikiArticle

interface WikiRepository {

    /**
     * O acervo inteiro, ordenado (destaque primeiro, depois `order_index`).
     *
     * Sem paginação e sem filtro por categoria de propósito: são ~12 artigos, o cliente é
     * offline-first (ARCH #30) e cacheia tudo, e filtrar por chip é operação de tela. Uma ida ao
     * servidor por toque de chip seria rede gasta para reordenar doze linhas que já estão no
     * aparelho.
     */
    suspend fun findAll(idioma: Idioma): AppResult<List<WikiArticle>>
}
