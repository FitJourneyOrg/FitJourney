package dev.rafael.features.wiki.data

import dev.rafael.contract.i18n.Idioma
import dev.rafael.contract.wiki.WikiArticleDto
import dev.rafael.contract.wiki.WikiCategory
import dev.rafael.core.database.SyncStamps
import dev.rafael.core.result.AppError
import dev.rafael.core.result.AppResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import dev.rafael.core.database.WikiArticle as WikiArticleRow

/**
 * O primeiro teste de um repositório do cliente SEM banco: só foi possível porque o `SyncStamps`
 * virou interface (B2). É a asserção que a fatia I.2 não conseguiu fazer no repositório e que
 * teve de ir para o ViewModel (ver `WikiViewModelTest`).
 *
 * O que se trava aqui é a lição da fatia H: **carimbo diz QUANDO foi baixado, não O QUE está
 * guardado**, então a decisão de ir à rede olha o carimbo E o idioma guardado.
 */
class WikiRepositoryImplTest {

    private class FakeRemote : WikiRemoteDataSource {
        var artigos: List<WikiArticleDto> = listOf(dto("a"), dto("b"))
        var falha: Throwable? = null
        val idiomasPedidos = mutableListOf<Idioma>()

        override suspend fun getArticles(idioma: Idioma): List<WikiArticleDto> {
            idiomasPedidos += idioma
            falha?.let { throw it }
            return artigos
        }
    }

    private class FakeLocal(
        var guardados: List<WikiArticleDto> = emptyList(),
        var idioma: String? = null,
    ) : WikiLocalDataSource {
        var substituicoes = 0

        override fun observeAll(): Flow<List<WikiArticleRow>> = flowOf(emptyList())
        override fun observeBySlug(slug: String): Flow<WikiArticleRow?> = flowOf(null)
        override fun isEmpty(): Boolean = guardados.isEmpty()
        override fun idiomaGuardado(): String? = idioma
        override fun replaceAll(dtos: List<WikiArticleDto>, idioma: String) {
            substituicoes++
            guardados = dtos
            this.idioma = idioma
        }
    }

    private class Cenario(
        var idiomaAtual: Idioma = Idioma.PT_BR,
        val remote: FakeRemote = FakeRemote(),
        val local: FakeLocal = FakeLocal(),
        val stamps: FakeSyncStamps = FakeSyncStamps(),
    ) {
        val repo = WikiRepositoryImpl(remote, local, stamps) { idiomaAtual }
    }

    @Test
    fun `primeira abertura baixa o acervo, guarda o idioma e carimba`() = runTest {
        val c = Cenario()

        val resultado = c.repo.refresh()

        assertIs<AppResult.Success<Unit>>(resultado)
        assertEquals(listOf(Idioma.PT_BR), c.remote.idiomasPedidos)
        assertEquals(2, c.local.guardados.size)
        assertEquals("pt-BR", c.local.idioma)
        assertEquals(
            listOf("wiki:pt-BR" to SyncStamps.Escopo.GLOBAL), c.stamps.marcados,
            "o acervo é do aparelho (GLOBAL) e a chave leva o idioma",
        )
    }

    @Test
    fun `carimbo fresco com o acervo no mesmo idioma nao vai a rede`() = runTest {
        val c = Cenario(local = FakeLocal(guardados = listOf(dto("a")), idioma = "pt-BR"))
        c.stamps.marcar("wiki:pt-BR", SyncStamps.Escopo.GLOBAL)

        c.repo.refresh()

        assertTrue(c.remote.idiomasPedidos.isEmpty(), "foi à rede com tudo fresco e no idioma certo")
        assertEquals(0, c.local.substituicoes)
    }

    @Test
    fun `forcar ignora o carimbo fresco`() = runTest {
        val c = Cenario(local = FakeLocal(guardados = listOf(dto("a")), idioma = "pt-BR"))
        c.stamps.marcar("wiki:pt-BR", SyncStamps.Escopo.GLOBAL)

        c.repo.refresh(forcar = true)

        assertEquals(1, c.remote.idiomasPedidos.size, "o arraste-para-atualizar não furou o TTL")
    }

    @Test
    fun `carimbo vencido rebaixa`() = runTest {
        val c = Cenario(local = FakeLocal(guardados = listOf(dto("a")), idioma = "pt-BR"))
        c.stamps.marcar("wiki:pt-BR", SyncStamps.Escopo.GLOBAL)
        c.stamps.agora += 25 * 60 * 60 * 1000L   // 25h depois: passou do TTL de 24h

        c.repo.refresh()

        assertEquals(1, c.remote.idiomasPedidos.size, "o acervo nunca atualiza depois de um deploy")
    }

    /**
     * ⭐ A volta do idioma: o carimbo `wiki:pt-BR` está fresco de ontem e a tabela não está vazia,
     * mas o que está GUARDADO é inglês. Sem comparar o idioma guardado, o app serviria o acervo em
     * inglês a quem voltou ao português.
     */
    @Test
    fun `voltar ao portugues rebaixa mesmo com o carimbo de portugues fresco`() = runTest {
        val c = Cenario(
            idiomaAtual = Idioma.PT_BR,
            local = FakeLocal(guardados = listOf(dto("a")), idioma = "en"),
        )
        c.stamps.marcar("wiki:pt-BR", SyncStamps.Escopo.GLOBAL)

        c.repo.refresh()

        assertEquals(listOf(Idioma.PT_BR), c.remote.idiomasPedidos)
        assertEquals("pt-BR", c.local.idioma, "o acervo continuou em inglês")
    }

    /** Caminho de falha (C2): a rede cai no meio do refresh. */
    @Test
    fun `rede caiu, devolve Connection, preserva o acervo local e nao carimba`() = runTest {
        val local = FakeLocal(guardados = listOf(dto("velho")), idioma = "pt-BR")
        val c = Cenario(local = local)
        c.remote.falha = RuntimeException("sem rede")

        val resultado = c.repo.refresh()

        val falha = assertIs<AppResult.Failure>(resultado)
        assertIs<AppError.Connection>(falha.error)
        assertEquals(listOf("velho"), local.guardados.map { it.slug }, "a falha apagou o acervo local")
        assertEquals(0, local.substituicoes)
        assertTrue(c.stamps.marcados.isEmpty(), "carimbou um download que não aconteceu")
    }
}

private fun dto(slug: String) = WikiArticleDto(
    id = "id-$slug", slug = slug, category = WikiCategory.TRAINING,
    title = "Titulo", body = "Corpo", orderIndex = 1, updatedAt = "2026-06-15",
)
