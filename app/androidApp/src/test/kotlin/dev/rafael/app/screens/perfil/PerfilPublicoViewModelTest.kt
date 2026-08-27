package dev.rafael.app.screens.perfil

import dev.rafael.app.data.perfil.PerfisPublicos
import dev.rafael.contract.user.PublicProfileDto
import dev.rafael.core.result.AppError
import dev.rafael.core.result.AppResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

    /** Conta as chamadas: é como se verifica que o [PerfilPublicoViewModel.carregar] não repete. */
    private class FakePerfis(private var resposta: AppResult<PublicProfileDto>) : PerfisPublicos {
        var chamadas = 0
        fun responder(nova: AppResult<PublicProfileDto>) { resposta = nova }
        override suspend fun de(userId: String): AppResult<PublicProfileDto> {
            chamadas++
            return resposta
        }
    }

    @Test
    fun `carrega o perfil e sai do estado de carregando`() = runTest(dispatcher) {
        val vm = PerfilPublicoViewModel(FakePerfis(AppResult.Success(perfil())))

        vm.carregar("u2")
        advanceUntilIdle()

        val s = vm.state.value
        assertEquals("Fulano", s.perfil?.displayName)
        assertFalse(s.carregando)
        assertNull(s.erro)
    }

    @Test
    fun `falha vira erro na tela, sem perfil`() = runTest(dispatcher) {
        val vm = PerfilPublicoViewModel(FakePerfis(AppResult.Failure(AppError.Connection())))

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
        val vm = PerfilPublicoViewModel(perfis)

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
        val vm = PerfilPublicoViewModel(perfis)

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
        val vm = PerfilPublicoViewModel(perfis)

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
        val vm = PerfilPublicoViewModel(perfis)

        vm.recarregar()
        advanceUntilIdle()

        assertEquals(0, perfis.chamadas, "sem id, não há o que recarregar")
    }
}
