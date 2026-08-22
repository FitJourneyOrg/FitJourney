package dev.rafael.app.screens.conta

import dev.rafael.app.data.me.Me
import dev.rafael.app.data.sessao.SairDaConta
import dev.rafael.app.screens.home.FakeAuth
import dev.rafael.app.screens.home.FakePerfil
import dev.rafael.contract.user.UserDto
import dev.rafael.core.result.AppError
import dev.rafael.core.result.AppResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tela de conta (ARCH #34): renomear e sair.
 *
 * `FakeAuth`/`FakePerfil` vêm de `screens.home` de propósito — são os mesmos dublês, e duplicá-los
 * aqui criaria duas versões da mesma mentira, que é como um teste começa a passar por engano.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ContaViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest fun setup() { Dispatchers.setMain(dispatcher) }
    @AfterTest fun tearDown() { Dispatchers.resetMain() }

    private class FakeMe(nomeInicial: String = "Rafael") : Me {
        val fluxo = MutableStateFlow<UserDto?>(
            UserDto(id = "u1", displayName = nomeInicial, email = "r@x.com", isPremium = false),
        )
        var pedidoDeRenome: String? = null
        var erro: AppError? = null

        override fun observar(): Flow<UserDto?> = fluxo
        override suspend fun sincronizar(forcar: Boolean) = Unit
        override suspend fun renomear(nome: String): AppResult<String> {
            pedidoDeRenome = nome
            val e = erro
            if (e != null) return AppResult.Failure(e)
            // Espelha o servidor: quem vale é a RESPOSTA normalizada, não o texto digitado.
            val normalizado = nome.trim().replace(Regex("\\s+"), " ")
            fluxo.value = fluxo.value?.copy(displayName = normalizado)
            return AppResult.Success(normalizado)
        }
    }

    private fun vm(me: FakeMe = FakeMe()) = ContaViewModel(me, SairDaConta(FakeAuth(), FakePerfil()))

    @Test
    fun `mostra o nome vindo do cache`() = runTest(dispatcher) {
        val viewModel = vm()
        advanceUntilIdle()

        assertEquals("Rafael", viewModel.state.value.nome)
        assertEquals("r@x.com", viewModel.state.value.email)
    }

    @Test
    fun `salvar so habilita quando o nome MUDOU`() = runTest(dispatcher) {
        // Sem isto, tocar em "Editar" e "Salvar" sem mexer em nada dispara um PATCH que não
        // altera coisa alguma — requisição e risco de erro por nada.
        val viewModel = vm()
        advanceUntilIdle()
        viewModel.editar()

        assertFalse(viewModel.state.value.podeSalvar, "rascunho igual ao nome atual")

        viewModel.aoDigitar("Rafael Souza")
        assertTrue(viewModel.state.value.podeSalvar)
    }

    @Test
    fun `a tela mostra o nome NORMALIZADO pelo servidor, nao o digitado`() = runTest(dispatcher) {
        // Se exibisse o digitado, "Rafael   Souza" ficaria com três espaços na tela até o
        // próximo sync — e aí o nome mudaria sozinho na cara do usuário.
        val me = FakeMe()
        val viewModel = vm(me)
        advanceUntilIdle()

        viewModel.editar()
        viewModel.aoDigitar("  Rafael   Souza ")
        viewModel.salvar()
        advanceUntilIdle()

        assertEquals("  Rafael   Souza ", me.pedidoDeRenome, "o cru vai pro servidor, que decide")
        assertEquals("Rafael Souza", viewModel.state.value.nome)
        assertFalse(viewModel.state.value.editando, "sucesso fecha a edição")
    }

    @Test
    fun `erro de validacao mantem a edicao aberta e guarda o fieldError`() = runTest(dispatcher) {
        // Fechar a edição no erro apagaria o que a pessoa digitou junto com a chance de
        // corrigir. [REGRA] quem valida é o servidor — a tela só apresenta o que voltou.
        val me = FakeMe().apply {
            erro = AppError.Validation(
                message = "Use pelo menos 2 caracteres.",
                fieldErrors = mapOf("displayName" to "Use pelo menos 2 caracteres."),
            )
        }
        val viewModel = vm(me)
        advanceUntilIdle()

        viewModel.editar()
        viewModel.aoDigitar("R")
        viewModel.salvar()
        advanceUntilIdle()

        assertTrue(viewModel.state.value.editando)
        assertFalse(viewModel.state.value.salvando)
        assertEquals("Rafael", viewModel.state.value.nome, "o nome de verdade não mudou")
        assertEquals(
            "Use pelo menos 2 caracteres.",
            (viewModel.state.value.erro as AppError.Validation).fieldErrors["displayName"],
        )
    }

    @Test
    fun `digitar limpa o erro`() = runTest(dispatcher) {
        val me = FakeMe().apply { erro = AppError.Validation() }
        val viewModel = vm(me)
        advanceUntilIdle()
        viewModel.editar()
        viewModel.aoDigitar("R")
        viewModel.salvar()
        advanceUntilIdle()

        viewModel.aoDigitar("Ra")

        assertNull(viewModel.state.value.erro, "campo vermelho enquanto se corrige é ruído")
    }

    @Test
    fun `sincronizacao no meio da edicao NAO apaga o que se esta digitando`() = runTest(dispatcher) {
        val me = FakeMe()
        val viewModel = vm(me)
        advanceUntilIdle()
        viewModel.editar()
        viewModel.aoDigitar("Rafael S")

        // Um GET /me chegando agora (o TTL venceu, o worker rodou) re-emite no Flow.
        me.fluxo.value = me.fluxo.value?.copy(displayName = "Rafael")
        advanceUntilIdle()

        assertEquals("Rafael S", viewModel.state.value.rascunho, "o texto na mão do usuário é dele")
    }

    @Test
    fun `sair limpa o cache de onboarding antes de deslogar`() = runTest(dispatcher) {
        // Ordem importa: sem limpar, o próximo cadastro herda o `true` e pula o quiz.
        // (teste migrado da HomeViewModelTest — o logout mudou de casa no ARCH #34)
        val perfil = FakePerfil()
        val auth = FakeAuth()
        val viewModel = ContaViewModel(FakeMe(), SairDaConta(auth, perfil))
        advanceUntilIdle()

        viewModel.sair()
        advanceUntilIdle()

        assertTrue(perfil.limpouCache)
        assertTrue(auth.deslogou)
        assertTrue(viewModel.saiu.value)
    }
}
