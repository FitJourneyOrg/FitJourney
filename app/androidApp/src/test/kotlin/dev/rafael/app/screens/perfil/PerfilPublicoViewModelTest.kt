package dev.rafael.app.screens.perfil

import dev.rafael.app.data.amizades.Amizades
import dev.rafael.contract.friendship.FriendRequestDto
import dev.rafael.contract.friendship.PersonDto
import dev.rafael.app.data.perfil.PerfisPublicos
import dev.rafael.contract.user.PublicProfileDto
import dev.rafael.core.result.AppError
import dev.rafael.core.result.AppResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Perfil de outra pessoa (C.1).
 *
 * ## Por que estes testes existem, e não outros
 *
 * A bateria manual cobriu bem os toques — ranking, posts, membros, o X do admin. O que ela
 * **não conseguiu alcançar** foi o caminho de erro: em modo avião a tela do grupo já barra
 * antes, com o aviso de offline, então nunca se chega a tocar num nome. Só derrubando o Ktor
 * com o Wi-Fi ligado, que é um arranjo que ninguém repete toda semana.
 *
 * Caminho que a mão não alcança é caminho que precisa de teste automatizado. É a versão desta
 * fatia da regra "todo defeito que escapou vira teste" — aqui nada escapou, mas o caminho
 * escapou da bateria, que dá no mesmo daqui a três meses.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PerfilPublicoViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest fun setup() { Dispatchers.setMain(dispatcher) }
    @AfterTest fun tearDown() { Dispatchers.resetMain() }

    private fun perfil(id: String = "u2") = PublicProfileDto(
        userId = id,
        displayName = "Fulano",
        level = 4,
        xp = 1200,
    )

    /**
     * Registra o que foi chamado — é como se verifica que a ação foi para o servidor E que o
     * perfil foi recarregado depois, em vez de a tela adivinhar o botão novo.
     */
    private class FakeAmizades : Amizades {
        val chamadas = mutableListOf<String>()
        var resposta: AppResult<Unit> = AppResult.Success(Unit)

        /**
         * Trava a ação no meio do caminho, para o teste observar o estado "em voo".
         *
         * Sem isto não dá para verificar `agindo`: com `StandardTestDispatcher` a corrotina só
         * roda no `advanceUntilIdle()`, e aí ela já terminou. Um fake que responde na hora não
         * tem "meio do caminho" para observar.
         */
        var portao: CompletableDeferred<Unit>? = null

        private suspend fun registrar(o: String): AppResult<Unit> {
            chamadas += o
            portao?.await()
            return resposta
        }

        override suspend fun pedir(userId: String) = registrar("pedir")
        override suspend fun aceitar(userId: String) = registrar("aceitar")
        override suspend fun recusar(userId: String) = registrar("recusar")
        override suspend fun remover(userId: String) = registrar("remover")
        override suspend fun bloquear(userId: String) = registrar("bloquear")
        override suspend fun desbloquear(userId: String) = registrar("desbloquear")

        override suspend fun amigos() = AppResult.Success(emptyList<PersonDto>())
        override suspend fun pedidosRecebidos() = AppResult.Success(emptyList<FriendRequestDto>())
        override suspend fun bloqueados() = AppResult.Success(emptyList<PersonDto>())
        override suspend fun porCodigo(codigo: String) = error("não usado")
        override suspend fun regenerarMeuCodigo() = error("não usado")
    }

    /** Conta as chamadas: é como se verifica que o [PerfilPublicoViewModel.carregar] não repete. */
    private class FakePerfis(private var resposta: AppResult<PublicProfileDto>) : PerfisPublicos {
        var chamadas = 0
        fun responder(nova: AppResult<PublicProfileDto>) { resposta = nova }
        override suspend fun de(userId: String): AppResult<PublicProfileDto> {
            chamadas++
            return resposta
        }
    }

    private fun viewModel(
        perfis: FakePerfis,
        amizades: FakeAmizades = FakeAmizades(),
    ) = PerfilPublicoViewModel(perfis, amizades)

    @Test
    fun `carrega o perfil e sai do estado de carregando`() = runTest(dispatcher) {
        val vm = viewModel(FakePerfis(AppResult.Success(perfil())))

        vm.carregar("u2")
        advanceUntilIdle()

        val s = vm.state.value
        assertEquals("Fulano", s.perfil?.displayName)
        assertFalse(s.carregando)
        assertNull(s.erro)
    }

    @Test
    fun `falha vira erro na tela, sem perfil`() = runTest(dispatcher) {
        val vm = viewModel(FakePerfis(AppResult.Failure(AppError.Connection())))

        vm.carregar("u2")
        advanceUntilIdle()

        val s = vm.state.value
        assertNull(s.perfil, "sem perfil, a tela mostra o erro inteiro (nível 1 do #31)")
        assertTrue(s.erro is AppError.Connection)
        assertFalse(s.carregando, "carregando tem que DESLIGAR na falha — senão gira para sempre")
    }

    @Test
    fun `carregar duas vezes com o mesmo id nao repete a requisicao`() = runTest(dispatcher) {
        val perfis = FakePerfis(AppResult.Success(perfil()))
        val vm = viewModel(perfis)

        vm.carregar("u2")
        advanceUntilIdle()
        // Volta da pilha / rotação: o ON_START dispara de novo.
        vm.carregar("u2")
        advanceUntilIdle()

        assertEquals(1, perfis.chamadas, "ON_START repetido não pode virar requisição repetida")
    }

    @Test
    fun `recarregar depois de falhar traz o perfil sem sair da tela`() = runTest(dispatcher) {
        val perfis = FakePerfis(AppResult.Failure(AppError.Connection()))
        val vm = viewModel(perfis)

        vm.carregar("u2")
        advanceUntilIdle()
        assertNotNull(vm.state.value.erro)

        // O servidor voltou — é o "Tentar de novo" da tela de erro.
        perfis.responder(AppResult.Success(perfil()))
        vm.recarregar()
        advanceUntilIdle()

        val s = vm.state.value
        assertEquals("Fulano", s.perfil?.displayName)
        assertNull(s.erro, "o erro anterior tem que SAIR — senão a tela mostra sucesso e erro juntos")
    }

    /**
     * Falhar ao recarregar **não apaga o perfil que já está na tela**.
     *
     * O contrário seria a pessoa lendo o perfil de alguém, o app tentando atualizar, a rede cair
     * e o conteúdo sumir debaixo dos olhos dela. Erro sobre conteúdo velho é melhor que tela
     * vazia — o dado antigo continua verdadeiro, só não é o mais novo.
     */
    @Test
    fun `falha ao recarregar preserva o perfil ja exibido`() = runTest(dispatcher) {
        val perfis = FakePerfis(AppResult.Success(perfil()))
        val vm = viewModel(perfis)

        vm.carregar("u2")
        advanceUntilIdle()

        perfis.responder(AppResult.Failure(AppError.Connection()))
        vm.recarregar()
        advanceUntilIdle()

        val s = vm.state.value
        assertEquals("Fulano", s.perfil?.displayName, "o que estava na tela continua lá")
        assertTrue(s.erro is AppError.Connection)
    }

    @Test
    fun `recarregar antes de qualquer carregar nao faz nada`() = runTest(dispatcher) {
        val perfis = FakePerfis(AppResult.Success(perfil()))
        val vm = viewModel(perfis)

        vm.recarregar()
        advanceUntilIdle()

        assertEquals(0, perfis.chamadas, "sem id, não há o que recarregar")
    }

    // ---- ações do grafo (#35) ----

    /**
     * [INVARIANTE] Toda ação do grafo RECARREGA o perfil.
     *
     * É o que garante que o botão novo venha do servidor. Trocá-lo na tela estaria errado em três
     * casos que só o servidor conhece: bloqueio, teto de 500, e o pedido cruzado que vira amizade
     * direto. Adivinhar aqui seria reimplementar a regra no cliente.
     */
    @Test
    fun `cada acao do grafo recarrega o perfil depois`() = runTest(dispatcher) {
        val perfis = FakePerfis(AppResult.Success(perfil()))
        val amizades = FakeAmizades()
        val vm = viewModel(perfis, amizades)

        vm.carregar("u2")
        advanceUntilIdle()
        val antes = perfis.chamadas

        listOf<(String) -> Unit>(
            vm::pedir, vm::aceitar, vm::recusar, vm::remover, vm::bloquear, vm::desbloquear,
        ).forEach { acao ->
            acao("u2")
            advanceUntilIdle()
        }

        assertEquals(
            listOf("pedir", "aceitar", "recusar", "remover", "bloquear", "desbloquear"),
            amizades.chamadas,
            "todas as seis ações precisam chegar ao servidor",
        )
        assertEquals(
            antes + 6,
            perfis.chamadas,
            "uma recarga por ação — o botão certo é o que o servidor devolve",
        )
    }

    @Test
    fun `acao que falha mostra erro SEM apagar o perfil`() = runTest(dispatcher) {
        val perfis = FakePerfis(AppResult.Success(perfil()))
        val amizades = FakeAmizades()
        val vm = viewModel(perfis, amizades)

        vm.carregar("u2")
        advanceUntilIdle()

        amizades.resposta = AppResult.Failure(AppError.Connection())
        vm.pedir("u2")
        advanceUntilIdle()

        val s = vm.state.value
        assertEquals("Fulano", s.perfil?.displayName, "falhar ao adicionar não pode limpar a tela")
        assertTrue(s.erroDaAcao is AppError.Connection, "o erro da AÇÃO fica ao lado do botão")
        assertNull(s.erro, "e não vira erro de tela inteira — são dois eixos diferentes")
        assertFalse(s.agindo, "o botão tem que voltar a funcionar")
    }

    /**
     * O botão desabilita **enquanto a ação está em voo**, e volta quando ela termina.
     *
     * O `portao` é o que torna isto observável: ele segura a chamada dentro do `agir`, o
     * `runCurrent()` deixa a corrotina chegar até lá, e só então o estado é lido. Foi assim que
     * este teste passou a valer alguma coisa — a primeira versão lia o estado antes de a
     * corrotina sequer começar, e "passava" por acidente de agendamento.
     */
    @Test
    fun `acao em voo desabilita o botao`() = runTest(dispatcher) {
        val amizades = FakeAmizades()
        val vm = viewModel(FakePerfis(AppResult.Success(perfil())), amizades)
        vm.carregar("u2")
        advanceUntilIdle()

        amizades.portao = CompletableDeferred()
        vm.pedir("u2")
        runCurrent()

        assertTrue(vm.state.value.agindo, "enquanto está em voo, o botão não aceita segundo toque")

        amizades.portao!!.complete(Unit)
        advanceUntilIdle()
        assertFalse(vm.state.value.agindo, "terminou: o botão volta a funcionar")
    }
}
