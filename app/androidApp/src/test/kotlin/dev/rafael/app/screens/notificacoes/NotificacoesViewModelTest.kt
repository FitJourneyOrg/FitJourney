package dev.rafael.app.screens.notificacoes

import dev.rafael.app.data.notificacoes.ContadorDeNaoLidas
import dev.rafael.app.data.notificacoes.Notificacoes
import dev.rafael.contract.notificacao.NotificacaoDto
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A central de notificações (F.1).
 *
 * ## O que a bateria manual não alcança aqui
 *
 * O destaque do não-lido dura **um instante**: abrir a tela marca tudo como lido no servidor, e
 * o que o olho vê é o snapshot anterior. Numa bateria manual, uma implementação que apagasse o
 * destaque na hora e uma que o preserva são indistinguíveis se a lista recarregar rápido — e a
 * diferença é justamente o motivo de a pessoa ter aberto a tela.
 *
 * O outro caminho invisível é o inverso: uma central **sem** não-lidas não pode disparar a
 * marcação. Sem este teste, o app mandaria um POST a cada abertura da tela para não fazer nada.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NotificacoesViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest fun setup() { Dispatchers.setMain(dispatcher) }
    @AfterTest fun tearDown() { Dispatchers.resetMain() }

    private fun nota(id: String, lida: Boolean) = NotificacaoDto(
        id = id,
        type = "PEDIDO_DE_AMIZADE",
        title = "Fulano quer ser seu amigo",
        body = "Toque para ver o pedido",
        readAt = if (lida) "2026-08-29T10:00:00" else null,
        createdAt = "2026-08-29T09:00:00",
    )

    /** Registra as chamadas: é como se verifica QUANDO a marcação sai — e quando ela não sai. */
    private class FakeNotificacoes(var resposta: AppResult<List<NotificacaoDto>>) : Notificacoes {
        var marcacoes = 0
        override suspend fun listar() = resposta
        override suspend fun marcarComoLidas(): AppResult<Unit> {
            marcacoes++
            return AppResult.Success(Unit)
        }
        override suspend fun registrarDispositivo(token: String) = error("não usado")
        override suspend fun darBaixaNoDispositivo(token: String) = error("não usado")
    }

    private fun cenario(resposta: AppResult<List<NotificacaoDto>>):
        Triple<NotificacoesViewModel, FakeNotificacoes, ContadorDeNaoLidas> {
        val fonte = FakeNotificacoes(resposta)
        val contador = ContadorDeNaoLidas(fonte)
        return Triple(NotificacoesViewModel(fonte, contador), fonte, contador)
    }

    @Test
    fun `carrega a lista e sai do estado de carregando`() = runTest(dispatcher) {
        val (vm, _, _) = cenario(AppResult.Success(listOf(nota("1", lida = true))))

        vm.carregar()
        advanceUntilIdle()

        val s = vm.state.value
        assertEquals(1, s.itens.size)
        assertFalse(s.carregando)
        assertNull(s.erro)
    }

    /**
     * [INVARIANTE] Abrir marca como lido, MAS a lista exibida mantém o `readAt = null`.
     *
     * É o snapshot anterior à marcação que a tela desenha, e é ele que carrega o destaque. Se o
     * ViewModel recarregasse depois de marcar, tudo viraria "lido" na hora e a pessoa perderia
     * exatamente a informação que foi buscar.
     */
    @Test
    fun `abrir marca como lidas e zera o badge, sem apagar o destaque da lista`() = runTest(dispatcher) {
        val (vm, fonte, contador) = cenario(
            AppResult.Success(listOf(nota("1", lida = false), nota("2", lida = true))),
        )
        contador.atualizar()
        assertEquals(1, contador.quantidade.value)

        vm.carregar()
        advanceUntilIdle()

        assertEquals(1, fonte.marcacoes, "abrir a central é o gesto de 'vi tudo isto'")
        assertEquals(0, contador.quantidade.value, "o badge da barra apaga junto, sem recarregar")
        assertNull(
            vm.state.value.itens.first().readAt,
            "o item continua marcado como novo NA TELA — é o destaque que a pessoa veio ver",
        )
    }

    /** Sem não-lidas não há o que marcar: um POST por abertura de tela para nada. */
    @Test
    fun `central sem nao lidas nao chama a marcacao`() = runTest(dispatcher) {
        val (vm, fonte, _) = cenario(AppResult.Success(listOf(nota("1", lida = true))))

        vm.carregar()
        advanceUntilIdle()

        assertEquals(0, fonte.marcacoes)
    }

    @Test
    fun `central vazia nao chama a marcacao`() = runTest(dispatcher) {
        val (vm, fonte, _) = cenario(AppResult.Success(emptyList()))

        vm.carregar()
        advanceUntilIdle()

        assertEquals(0, fonte.marcacoes)
        assertFalse(vm.state.value.carregando, "lista vazia é resposta, não espera eterna")
    }

    @Test
    fun `falha vira erro sem apagar o que ja estava na tela`() = runTest(dispatcher) {
        val (vm, fonte, _) = cenario(AppResult.Success(listOf(nota("1", lida = true))))

        vm.carregar()
        advanceUntilIdle()

        // Voltar para a tela com a rede fora do ar.
        fonte.resposta = AppResult.Failure(AppError.Connection())
        vm.carregar()
        advanceUntilIdle()

        val s = vm.state.value
        assertEquals(1, s.itens.size, "o conteúdo antigo continua verdadeiro, só não é o mais novo")
        assertTrue(s.erro is AppError.Connection)
        assertFalse(s.carregando, "carregando tem que DESLIGAR na falha — senão gira para sempre")
    }

    /** Falhar não pode marcar nada como lido: seria apagar o badge de algo nunca exibido. */
    @Test
    fun `falha nao marca como lidas`() = runTest(dispatcher) {
        val (vm, fonte, contador) = cenario(AppResult.Success(listOf(nota("1", lida = false))))
        contador.atualizar()

        fonte.resposta = AppResult.Failure(AppError.Connection())
        val marcacoesAntes = fonte.marcacoes
        vm.carregar()
        advanceUntilIdle()

        assertEquals(marcacoesAntes, fonte.marcacoes)
        assertEquals(1, contador.quantidade.value, "o badge segue aceso — nada foi visto")
    }
}
