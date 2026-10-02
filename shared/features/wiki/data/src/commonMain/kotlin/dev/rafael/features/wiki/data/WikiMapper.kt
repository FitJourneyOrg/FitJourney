package dev.rafael.features.wiki.data

import dev.rafael.contract.wiki.WikiCategory
import dev.rafael.features.wiki.domain.model.WikiArticle
import dev.rafael.core.database.WikiArticle as WikiArticleRow

/**
 * Linha do cache local → domínio.
 *
 * `OrNull` pelo mesmo motivo do `ExerciseMapper`: categoria que não existe neste app veio de um
 * servidor mais novo, e é melhor **perder um artigo do que derrubar a lista inteira**. Quem chama
 * usa `mapNotNull`, então o resto do acervo continua na tela.
 */
fun WikiArticleRow.toDomainOrNull(): WikiArticle? {
    val categoria = runCatching { WikiCategory.valueOf(category) }.getOrNull() ?: return null
    return WikiArticle(
        id = id,
        slug = slug,
        category = categoria,
        title = title,
        body = body,
        featured = featured == 1L,
        orderIndex = orderIndex.toInt(),
        updatedAt = updatedAt,
    )
}
