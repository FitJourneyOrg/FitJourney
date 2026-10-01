package dev.rafael.server.features.wiki.db

import dev.rafael.contract.i18n.Idioma
import dev.rafael.contract.wiki.WikiCategory
import dev.rafael.core.result.AppError
import dev.rafael.core.result.AppResult
import dev.rafael.core.result.asFailure
import dev.rafael.core.result.asSuccess
import dev.rafael.server.features.wiki.models.WikiArticle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.core.ColumnSet
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

class WikiRepositoryImpl : WikiRepository {

    /**
     * Mesma forma do `ExerciseRepositoryImpl.origem` (V49), pela mesma razão — e as duas frases que
     * importam lá valem idênticas aqui:
     *
     * O `PADRAO` **não faz join**, porque `wiki_articles.title`/`body` já SÃO o pt-BR: juntar com
     * uma tabela que nunca terá linha `pt-BR` seria pagar join para receber `null`.
     *
     * O `LEFT` é obrigatório: com `INNER`, artigo ainda não traduzido **sumiria do acervo** em vez
     * de aparecer no piso — a pessoa trocaria de idioma e o app perderia conteúdo, sem erro nenhum.
     */
    private fun origem(idioma: Idioma): ColumnSet =
        if (idioma == Idioma.PADRAO) {
            WikiArticlesTable
        } else {
            WikiArticlesTable.join(
                otherTable = WikiArticleTranslationsTable,
                joinType = JoinType.LEFT,
                onColumn = WikiArticlesTable.id,
                otherColumn = WikiArticleTranslationsTable.articleId,
                additionalConstraint = { WikiArticleTranslationsTable.locale eq idioma.tag },
            )
        }

    override suspend fun findAll(idioma: Idioma): AppResult<List<WikiArticle>> =
        dbQuery {
            origem(idioma).selectAll()
                .orderBy(
                    WikiArticlesTable.featured to SortOrder.DESC,
                    WikiArticlesTable.orderIndex to SortOrder.ASC,
                )
                .map { it.toArtigoTraduzido(idioma) }
        }

    private suspend fun <T> dbQuery(block: () -> T): AppResult<T> =
        withContext(Dispatchers.IO) {
            runCatching { transaction { block() } }.fold(
                onSuccess = { it.asSuccess() },
                onFailure = { AppError.Unexpected("Erro de banco", it).asFailure() },
            )
        }
}

/**
 * Lê a linha já sabendo se veio de um join — no `PADRAO` a coluna traduzida **não existe na
 * consulta**, e pedi-la a um `ResultRow` estoura. Por isso a checagem do idioma vem antes do
 * `getOrNull`, e não depois (a mesma pegadinha documentada no `ExerciseRepositoryImpl`).
 *
 * Título e corpo caem juntos: ou os dois vêm da tradução, ou os dois vêm do piso. A V61 garante
 * isso no schema (os dois NOT NULL na mesma linha), e o `?:` aqui não os separa por acidente.
 */
private fun ResultRow.toArtigoTraduzido(idioma: Idioma): WikiArticle {
    val traduzido = if (idioma == Idioma.PADRAO) {
        null
    } else {
        getOrNull(WikiArticleTranslationsTable.title)?.let { t ->
            t to getOrNull(WikiArticleTranslationsTable.body)
        }
    }
    return WikiArticle(
        id = this[WikiArticlesTable.id],
        slug = this[WikiArticlesTable.slug],
        // `runCatching` como o resto do projeto faz com enum vindo do banco: categoria
        // desconhecida (escrita direta, migration futura) não derruba o acervo inteiro.
        category = runCatching { WikiCategory.valueOf(this[WikiArticlesTable.category]) }
            .getOrDefault(WikiCategory.TRAINING),
        title = traduzido?.first ?: this[WikiArticlesTable.title],
        body = traduzido?.second ?: this[WikiArticlesTable.body],
        featured = this[WikiArticlesTable.featured],
        orderIndex = this[WikiArticlesTable.orderIndex],
        updatedAt = this[WikiArticlesTable.updatedAt],
    )
}
