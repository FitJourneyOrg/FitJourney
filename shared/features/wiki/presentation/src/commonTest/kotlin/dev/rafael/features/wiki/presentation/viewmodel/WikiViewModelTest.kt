package dev.rafael.features.wiki.presentation.viewmodel

import dev.rafael.contract.wiki.WikiCategory
import dev.rafael.core.result.AppError
import dev.rafael.core.result.AppResult
import dev.rafael.features.wiki.domain.model.WikiArticle
import dev.rafael.features.wiki.domain.repository.WikiRepository
import dev.rafael.features.wiki.presentation.state.WikiListEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class WikiViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest fun setup() { Dispatchers.setMain(dispatcher) }
    @AfterTest fun tearDown() { Dispatchers.resetMain() }

    private class FakeWikiRepository(
        var resultadoRefresh: AppResult<Unit> = AppResult.Success(Unit),
    ) : WikiRepository {
        val locais = MutableStateFlow<List<WikiArticle>>(emptyList())
        var refreshes = 0
        var forcados = 0

        override fun observeArticles(): Flow<List<WikiArticle>> = locais
        override fun observeArticle(slug: String): Flow<WikiArticle?> =
            locais.map { lista -> lista.firstOrNull { it.slug == slug } }

        override suspend fun refresh(forcar: Boolean): AppResult<Unit> {
            refreshes++
            if (forcar) forcados++
            return resultadoRefresh
        }
    }

    private fun artigo(
        slug: String,
        categoria: WikiCategory = WikiCategory.TRAINING,
        destaque: Boolean = false,
        ordem: Int = 0,
    ) = WikiArticle(
        id = "id-$slug", slug = slug, category = categoria,
        title = "Titulo de $slug", body = "Corpo", featured = destaque,
        orderIndex = ordem, updatedAt = "2026-06-15",
    )

    // ---- chips derivados ----

    @Test
    fun `os chips saem do que existe, nao do enum`() = runTest(dispatcher) {
        val repo = FakeWikiRepository()
        val vm = WikiViewModel(repo)
        advanceUntilIdle()

        repo.locais.value = listOf(
            artigo("a", WikiCategory.TRAINING),
            artigo("b", WikiCategory.NUTRITION),
        )
        advanceUntilIdle()

        // MINDSET/RECOVERY/TECHNIQUE não têm artigo: chip que leva a tela vazia não nasce.
        assertEquals(listOf(WikiCategory.TRAINING, WikiCategory.NUTRITION), vm.state.value.categorias)
    }

    @Test
    fun `a ordem dos chips e a do enum, nao a de chegada`() = runTest(dispatcher) {
        val repo = FakeWikiRepository()
        val vm = WikiViewModel(repo)
        advanceUntilIdle()

        // Chega Mentalidade antes de Treino; a fileira não pode dançar entre um sync e outro.
        repo.locais.value = listOf(
            artigo("a", WikiCategory.MINDSET),
            artigo("b", WikiCategory.TRAINING),
        )
        advanceUntilIdle()

        assertEquals(listOf(WikiCategory.TRAINING, WikiCategory.MINDSET), vm.state.value.categorias)
    }

    // ---- destaque ----

    @Test
    fun `o destaque sai da lista para nao aparecer duas vezes`() = runTest(dispatcher) {
        val repo = FakeWikiRepository()
        val vm = WikiViewModel(repo)
        advanceUntilIdle()

        repo.locais.value = listOf(artigo("comece-aqui", destaque = true), artigo("outro"))
        advanceUntilIdle()

        assertEquals("comece-aqui", vm.state.value.destaque?.slug)
        assertEquals(listOf("outro"), vm.state.value.artigos.map { it.slug })
    }

    @Test
    fun `com filtro ativo o destaque some do topo`() = runTest(dispatcher) {
        val repo = FakeWikiRepository()
        val vm = WikiViewModel(repo)
        advanceUntilIdle()
        repo.locais.value = listOf(
            artigo("comece-aqui", WikiCategory.TRAINING, destaque = true),
            artigo("prato", WikiCategory.NUTRITION),
        )
        advanceUntilIdle()

        vm.onEvent(WikiListEvent.CategoriaSelecionada(WikiCategory.NUTRITION))
        advanceUntilIdle()

        // Hero que ignora o filtro que a pessoa acabou de tocar parece defeito.
        assertNull(vm.state.value.destaque)
        assertEquals(listOf("prato"), vm.state.value.artigos.map { it.slug })
    }

    @Test
    fun `voltar para Tudo devolve o destaque ao topo`() = runTest(dispatcher) {
        val repo = FakeWikiRepository()
        val vm = WikiViewModel(repo)
        advanceUntilIdle()
        repo.locais.value = listOf(
            artigo("comece-aqui", WikiCategory.TRAINING, destaque = true),
            artigo("prato", WikiCategory.NUTRITION),
        )
        advanceUntilIdle()
        vm.onEvent(WikiListEvent.CategoriaSelecionada(WikiCategory.NUTRITION))
        advanceUntilIdle()

        vm.onEvent(WikiListEvent.CategoriaSelecionada(null))
        advanceUntilIdle()

        assertEquals("comece-aqui", vm.state.value.destaque?.slug)
    }

    // ---- carregamento e offline-first ----

    @Test
    fun `carregando so cai depois da primeira emissao do banco`() = runTest(dispatcher) {
        val repo = FakeWikiRepository()
        val vm = WikiViewModel(repo)

        // Antes de qualquer emissão: "ainda não sei" -- e não "o acervo está vazio".
        assertTrue(vm.state.value.carregando)

        advanceUntilIdle()
        assertEquals(false, vm.state.value.carregando)
    }

    /**
     * ⭐ Caminho de falha (C2) e o motivo de o cliente ser offline-first (ARCH #30).
     *
     * É a asserção que a I.2 não conseguiu fazer no repositório (o `SyncStamps` é concreto e exige
     * banco) e que, por decisão do Rafael, mora aqui — que é onde o projeto testa esse tipo de
     * comportamento.
     */
    @Test
    fun `rede caiu, o conteudo local continua na tela`() = runTest(dispatcher) {
        val repo = FakeWikiRepository(resultadoRefresh = AppResult.Failure(AppError.Connection()))
        val vm = WikiViewModel(repo)
        advanceUntilIdle()

        repo.locais.value = listOf(artigo("a"), artigo("b"))
        advanceUntilIdle()

        assertTrue(vm.state.value.error is AppError.Connection, "a falha aparece como aviso")
        assertEquals(
            listOf("a", "b"),
            vm.state.value.artigos.map { it.slug },
            "falhar o sync não pode apagar da tela o que já está no aparelho",
        )
    }

    @Test
    fun `consumir o erro de sync limpa o aviso e preserva o acervo`() = runTest(dispatcher) {
        val repo = FakeWikiRepository(resultadoRefresh = AppResult.Failure(AppError.Connection()))
        val vm = WikiViewModel(repo)
        advanceUntilIdle()
        repo.locais.value = listOf(artigo("a"), artigo("b"))
        advanceUntilIdle()
        assertTrue(vm.state.value.error is AppError.Connection)

        vm.consumeError()

        assertNull(vm.state.value.error)
        assertEquals(listOf("a", "b"), vm.state.value.artigos.map { it.slug })
    }

    @Test
    fun `um refresh que da certo depois da falha nao deixa erro velho`() = runTest(dispatcher) {
        val repo = FakeWikiRepository(resultadoRefresh = AppResult.Failure(AppError.Connection()))
        val vm = WikiViewModel(repo)
        advanceUntilIdle()
        assertTrue(vm.state.value.error is AppError.Connection)

        repo.resultadoRefresh = AppResult.Success(Unit)
        vm.onEvent(WikiListEvent.Refresh)
        advanceUntilIdle()

        assertNull(vm.state.value.error)
    }

    @Test
    fun `pull-to-refresh forca, o sync de abertura nao`() = runTest(dispatcher) {
        val repo = FakeWikiRepository()
        val vm = WikiViewModel(repo)
        advanceUntilIdle()
        assertEquals(0, repo.forcados, "o sync do init respeita o TTL de 24h")

        vm.onEvent(WikiListEvent.Refresh)
        advanceUntilIdle()

        assertEquals(1, repo.forcados)
    }
}
