package dev.rafael.features.wiki.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import dev.rafael.contract.wiki.WikiArticleDto
import dev.rafael.core.database.FitJourneyDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import dev.rafael.core.database.WikiArticle as WikiArticleRow

/** Ver o KDoc de [WikiRemoteDataSource] sobre por que estes dois são interface, e não classe. */
interface WikiLocalDataSource {
    fun observeAll(): Flow<List<WikiArticleRow>>
    fun observeBySlug(slug: String): Flow<WikiArticleRow?>
    fun isEmpty(): Boolean
    /** Em QUE IDIOMA está o acervo guardado. `null` = nunca baixou neste aparelho. */
    fun idiomaGuardado(): String?
    fun replaceAll(dtos: List<WikiArticleDto>, idioma: String)
}

class WikiLocalDataSourceSqlDelight(db: FitJourneyDatabase) : WikiLocalDataSource {

    private val queries = db.wikiQueries
    private val cache = db.cacheQueries

    override fun observeAll(): Flow<List<WikiArticleRow>> =
        queries.selectAll().asFlow().mapToList(Dispatchers.Default)

    override fun observeBySlug(slug: String): Flow<WikiArticleRow?> =
        queries.selectBySlug(slug).asFlow().mapToOneOrNull(Dispatchers.Default)

    override fun isEmpty(): Boolean = queries.countAll().executeAsOne() == 0L

    override fun idiomaGuardado(): String? = cache.get(CHAVE_IDIOMA).executeAsOneOrNull()

    /**
     * ⚠️ **O idioma é gravado na MESMA transação das linhas** — a lição literal da fatia H.
     *
     * Fora dela, uma falha entre o insert e o registro do idioma deixaria o banco dizendo que tem
     * português com o acervo em inglês dentro, e o app confiaria nele enquanto o carimbo estivesse
     * fresco.
     *
     * > **Metadado sobre um conjunto de linhas pertence à transação que escreveu as linhas.**
     */
    override fun replaceAll(dtos: List<WikiArticleDto>, idioma: String) {
        queries.transaction {
            queries.deleteAll()
            dtos.forEach { d ->
                queries.insertOrReplace(
                    id = d.id,
                    slug = d.slug,
                    category = d.category.name,
                    title = d.title,
                    body = d.body,
                    featured = if (d.featured) 1L else 0L,
                    orderIndex = d.orderIndex.toLong(),
                    updatedAt = d.updatedAt,
                )
            }
            cache.put(CHAVE_IDIOMA, idioma)
        }
    }

    private companion object {
        /** Sem uid: o acervo é do APARELHO, não da conta (o carimbo usa `Escopo.GLOBAL` pelo mesmo motivo). */
        const val CHAVE_IDIOMA = "wiki:idioma"
    }
}
