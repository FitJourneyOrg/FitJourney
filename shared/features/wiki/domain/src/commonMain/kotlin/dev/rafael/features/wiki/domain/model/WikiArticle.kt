package dev.rafael.features.wiki.domain.model

import dev.rafael.contract.wiki.WikiCategory

/**
 * Um artigo do acervo, no idioma que estava guardado quando o cache foi escrito.
 *
 * `updatedAt` é `String` (`yyyy-MM-dd`) e não um tipo de data: este módulo é Kotlin puro e o valor
 * só existe para ser FORMATADO pela tela, no idioma dela. Domínio que carrega data formatável sem
 * fazer conta com ela não ganha nada em tipá-la, e ganharia uma dependência.
 *
 * Não há tempo de leitura aqui: é função do tamanho do [body] e muda com o idioma, então é derivado
 * na apresentação (ver `WikiArticleDto`).
 */
data class WikiArticle(
    val id: String,
    val slug: String,
    val category: WikiCategory,
    val title: String,
    val body: String,
    val featured: Boolean,
    val orderIndex: Int,
    val updatedAt: String,
)
