package dev.rafael.server.features.wiki.services

import dev.rafael.contract.i18n.Idioma
import dev.rafael.contract.wiki.WikiArticleDto
import dev.rafael.core.result.AppResult
import dev.rafael.core.result.map
import dev.rafael.server.features.wiki.db.WikiRepository
import dev.rafael.server.features.wiki.models.toDto

/**
 * O acervo do "Aprender" (Fase 8).
 *
 * Fino de propósito: a ordenação é do SQL (destaque primeiro, depois `order_index`) e a tradução é
 * do repositório (LEFT JOIN com piso). O que sobra para cá é a conversão para DTO — e é bom que
 * seja pouco, porque tudo que pode quebrar neste recurso é SQL, e SQL se testa contra Postgres de
 * verdade, não contra fake.
 */
class WikiService(private val repository: WikiRepository) {

    suspend fun list(idioma: Idioma): AppResult<List<WikiArticleDto>> =
        repository.findAll(idioma).map { artigos -> artigos.map { it.toDto() } }
}
