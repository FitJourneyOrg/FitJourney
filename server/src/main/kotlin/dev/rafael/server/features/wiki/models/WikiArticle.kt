package dev.rafael.server.features.wiki.models

import dev.rafael.contract.wiki.WikiArticleDto
import dev.rafael.contract.wiki.WikiCategory
import kotlinx.datetime.LocalDateTime

/** Artigo do acervo, já com o texto resolvido no idioma pedido (ver `WikiRepositoryImpl`). */
data class WikiArticle(
    val id: kotlin.uuid.Uuid,
    val slug: String,
    val category: WikiCategory,
    val title: String,
    val body: String,
    val featured: Boolean,
    val orderIndex: Int,
    val updatedAt: LocalDateTime,
)

/**
 * `updatedAt` sai como `yyyy-MM-dd`: a hora não tem significado nenhum aqui (o valor vem do
 * frontmatter, que só tem data) e mandá-la convidaria a tela a exibi-la. A tela formata a data no
 * idioma dela — texto de data pronto vindo do servidor é o que a G.5 desfez.
 */
fun WikiArticle.toDto(): WikiArticleDto = WikiArticleDto(
    id = id.toString(),
    slug = slug,
    category = category,
    title = title,
    body = body,
    featured = featured,
    orderIndex = orderIndex,
    updatedAt = updatedAt.date.toString(),
)
