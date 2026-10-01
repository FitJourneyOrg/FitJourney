package dev.rafael.server.features.wiki.db

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.datetime

/** Espelha V61__wiki.sql. Acervo read-only pro usuário; escrita só pelo seed repetível. */
object WikiArticlesTable : Table("wiki_articles") {
    val id = uuid("id")
    val slug = varchar("slug", 80)
    val category = varchar("category", 16)     // WikiCategory.name
    val title = varchar("title", 200)          // pt-BR: é o PISO (ver V61)
    val body = text("body")                    // pt-BR, markdown mínimo
    val featured = bool("featured")
    val orderIndex = integer("order_index")
    val updatedAt = datetime("updated_at")     // data de revisão do TEXTO, não de escrita da linha

    override val primaryKey = PrimaryKey(id)
}
