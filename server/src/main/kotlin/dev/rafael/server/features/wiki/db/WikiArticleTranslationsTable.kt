package dev.rafael.server.features.wiki.db

import org.jetbrains.exposed.v1.core.Table

/**
 * Espelha V61__wiki.sql — o artigo por idioma, sem o pt-BR (que é o piso, em [WikiArticlesTable]).
 *
 * `title` e `body` são os dois NOT NULL, diferente do `description` nullable de
 * `exercise_translations`: prosa traduzida pela metade vira título em inglês com corpo em português
 * na mesma tela. Termo aceita meia tradução; artigo não.
 *
 * `locale` é `String` e não o enum `Idioma` pelo mesmo motivo da V47/V49: o que atravessa a
 * fronteira do banco viaja como texto, e idioma novo tem de ser INSERT, não mudança de tipo.
 */
object WikiArticleTranslationsTable : Table("wiki_article_translations") {
    val articleId = uuid("article_id").references(WikiArticlesTable.id)
    val locale = varchar("locale", 5)
    val title = varchar("title", 200)
    val body = text("body")

    override val primaryKey = PrimaryKey(articleId, locale)
}
