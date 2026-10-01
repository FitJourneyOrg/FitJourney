package dev.rafael.server.features.wiki.services

import dev.rafael.contract.i18n.Idioma
import dev.rafael.contract.wiki.WikiCategory
import dev.rafael.core.result.AppError
import dev.rafael.core.result.AppResult
import dev.rafael.server.features.wiki.db.WikiRepository
import dev.rafael.server.features.wiki.models.WikiArticle
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/**
 * O serviço é fino de propósito (ordenação é do SQL, tradução é do repositório), então o que sobra
 * para testar aqui é pouco — e isso é informação, não lacuna. O peso da fatia está no
 * `WikiIntegrationTest`, contra Postgres real, porque **tudo que pode quebrar neste recurso é SQL**:
 * o LEFT do join, o índice único parcial e os CHECKs de vocabulário. Nenhum deles existe no Kotlin.
 */
class WikiServiceTest {

    private fun artigo(
        slug: String,
        featured: Boolean = false,
        ordem: Int = 0,
    ) = WikiArticle(
        id = Uuid.random(),
        slug = slug,
        category = WikiCategory.TRAINING,
        title = "Titulo de $slug",
        body = "Corpo de $slug",
        featured = featured,
        orderIndex = ordem,
        updatedAt = LocalDateTime(2026, 6, 15, 0, 0),
    )

    private class FakeWikiRepository(
        private val resultado: AppResult<List<WikiArticle>>,
    ) : WikiRepository {
        var idiomaPedido: Idioma? = null
        override suspend fun findAll(idioma: Idioma): AppResult<List<WikiArticle>> {
            idiomaPedido = idioma
            return resultado
        }
    }

    @Test
    fun `converte para DTO preservando a ordem que o repositorio entregou`() = runBlocking {
        val repo = FakeWikiRepository(
            AppResult.Success(
                listOf(artigo("comece-aqui", featured = true), artigo("segundo", ordem = 1)),
            ),
        )

        val resultado = WikiService(repo).list(Idioma.PT_BR)

        val dtos = (resultado as AppResult.Success).value
        assertEquals(listOf("comece-aqui", "segundo"), dtos.map { it.slug }, "o serviço não reordena")
        assertTrue(dtos.first().featured)
    }

    @Test
    fun `a data viaja como yyyy-MM-dd, sem hora`() = runBlocking {
        val repo = FakeWikiRepository(AppResult.Success(listOf(artigo("um"))))

        val dtos = (WikiService(repo).list(Idioma.PT_BR) as AppResult.Success).value

        // A hora não significa nada aqui (o valor vem do frontmatter, que só tem data) e mandá-la
        // convidaria a tela a exibi-la.
        assertEquals("2026-06-15", dtos.single().updatedAt)
    }

    @Test
    fun `repassa o idioma pedido para o repositorio`() = runBlocking {
        val repo = FakeWikiRepository(AppResult.Success(emptyList()))

        WikiService(repo).list(Idioma.EN)

        assertEquals(Idioma.EN, repo.idiomaPedido)
    }

    /** Caminho de falha (C2): banco fora do ar não pode virar acervo vazio, que pareceria "não há conteúdo". */
    @Test
    fun `falha do repositorio propaga como falha, nao como lista vazia`() = runBlocking {
        val repo = FakeWikiRepository(AppResult.Failure(AppError.Unexpected("Erro de banco")))

        val resultado = WikiService(repo).list(Idioma.PT_BR)

        assertTrue(resultado is AppResult.Failure, "falha de banco não pode virar lista vazia")
    }
}
