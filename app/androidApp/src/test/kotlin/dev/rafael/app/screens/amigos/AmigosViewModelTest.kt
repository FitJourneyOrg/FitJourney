package dev.rafael.app.screens.amigos

import dev.rafael.app.data.amizades.Amizades
import dev.rafael.app.data.me.Me
import dev.rafael.contract.friendship.FriendRequestDto
import dev.rafael.contract.friendship.PersonDto
import dev.rafael.contract.i18n.Idioma
import dev.rafael.contract.user.PublicProfileDto
import dev.rafael.contract.user.UserDto
import dev.rafael.core.result.AppError
import dev.rafael.core.result.AppResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * B5: a lista de Pedidos não se mexe debaixo do dedo.
 *
 * O defeito original: um push recarregava a lista inteira, e um pedido novo entrava no meio das
 * linhas e deslocava os botões "Aceitar" no instante do toque ("aceitei o pedido errado"). O que se
 * trava aqui é a garantia de que **a lista visível só troca quando a PESSOA manda**.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AmigosViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest fun setup() { Dispatchers.setMain(dispatcher) }
    @AfterTest fun tearDown() { Dispatchers.resetMain() }

    private fun pedido(id: String) = FriendRequestDto(
        from = PersonDto(userId = id, displayName = "Pessoa $id"),
        createdAt = "2026-10-07T10:00:00",
    )

    private class FakeAmizades(var pedidos: AppResult<List<FriendRequestDto>>) : Amizades {
        var consultasDePedidos = 0
        var consultasDeAmigos = 0

        /** Quando não-nulo, a consulta de pedidos espera por ele (estado NO MEIO da carga). */
        var portao: CompletableDeferred<Unit>? = null

        override suspend fun amigos(): AppResult<List<PersonDto>> {
            consultasDeAmigos++
            return AppResult.Success(emptyList())
        }
        override suspend fun pedidosRecebidos(): AppResult<List<FriendRequestDto>> {
            consultasDePedidos++
            portao?.await()
            return pedidos
        }
        override suspend fun pedir(userId: String) = error("não usado")
        override suspend fun aceitar(userId: String) = error("não usado")
        override suspend fun recusar(userId: String) = error("não usado")
        override suspend fun remover(userId: String) = error("não usado")
        override suspend fun bloquear(userId: String) = error("não usado")
        override suspend fun desbloquear(userId: String) = error("não usado")
        override suspend fun bloqueados() = error("não usado")
        override suspend fun porCodigo(codigo: String): AppResult<PublicProfileDto> = error("não usado")
        override suspend fun regenerarMeuCodigo(): AppResult<UserDto> = error("não usado")
    }

    private class FakeMe : Me {
        override fun observar(): Flow<UserDto?> = flowOf(null)
        override suspend fun sincronizar(forcar: Boolean) = Unit
        override suspend fun renomear(nome: String): AppResult<String> = AppResult.Success(nome)
        override suspend fun definirIdioma(idioma: Idioma): AppResult<Unit> = AppResult.Success(Unit)
    }

    private fun lista(vararg ids: String) = AppResult.Success(ids.map(::pedido))

    /** Uma tela aberta com [ids] já na lista visível. */
    private fun kotlinx.coroutines.test.TestScope.telaCom(vararg ids: String): Pair<AmigosViewModel, FakeAmizades> {
        val fonte = FakeAmizades(lista(*ids))
        val vm = AmigosViewModel(fonte, FakeMe())
        vm.carregar()
        advanceUntilIdle()
        return vm to fonte
    }

    private val push = AmigosViewModel.TIPO_PEDIDO_DE_AMIZADE

    @Test
    fun `pedido novo com a lista visivel nao troca as linhas e vira aviso`() = runTest(dispatcher) {
        val (vm, fonte) = telaCom("a")
        fonte.pedidos = lista("a", "b")

        vm.aoChegarPush(push)
        advanceUntilIdle()

        val s = vm.state.value
        assertEquals(listOf("a"), s.pedidos.map { it.from.userId }, "a lista se moveu debaixo do dedo")
        assertEquals(1, s.pedidosNovos)
        assertEquals(2, s.pendentes, "o selo da aba tem de dizer a verdade na hora")
    }

    @Test
    fun `tocar no aviso troca a lista`() = runTest(dispatcher) {
        val (vm, fonte) = telaCom("a")
        fonte.pedidos = lista("a", "b")
        vm.aoChegarPush(push)
        advanceUntilIdle()

        vm.mostrarPedidosNovos()

        val s = vm.state.value
        assertEquals(listOf("a", "b"), s.pedidos.map { it.from.userId })
        assertEquals(0, s.pedidosNovos)
        assertNull(s.aguardando)
    }

    @Test
    fun `lista vazia recebe o pedido direto, sem aviso`() = runTest(dispatcher) {
        val (vm, fonte) = telaCom()
        fonte.pedidos = lista("a")

        vm.aoChegarPush(push)
        advanceUntilIdle()

        assertEquals(listOf("a"), vm.state.value.pedidos.map { it.from.userId })
        assertEquals(0, vm.state.value.pedidosNovos, "sem linha não há botão para deslocar")
    }

    @Test
    fun `push de outro tipo nao consulta nada`() = runTest(dispatcher) {
        val (vm, fonte) = telaCom("a")
        val antesPedidos = fonte.consultasDePedidos
        val antesAmigos = fonte.consultasDeAmigos

        vm.aoChegarPush("COMENTARIO_NO_CHECKIN")
        vm.aoChegarPush("CONQUISTA_DESBLOQUEADA")
        advanceUntilIdle()

        assertEquals(antesPedidos, fonte.consultasDePedidos, "comentário recarregou a lista de pedidos")
        assertEquals(antesAmigos, fonte.consultasDeAmigos, "comentário recarregou a lista de amigos")
    }

    @Test
    fun `push sem ninguem novo nao cria aviso`() = runTest(dispatcher) {
        val (vm, _) = telaCom("a", "b")

        vm.aoChegarPush(push)
        advanceUntilIdle()

        assertNull(vm.state.value.aguardando)
    }

    @Test
    fun `pedido que sumiu continua na tela ate a proxima recarga`() = runTest(dispatcher) {
        val (vm, fonte) = telaCom("a", "b")
        fonte.pedidos = lista("a")   // o "b" cancelou

        vm.aoChegarPush(push)
        advanceUntilIdle()

        assertEquals(listOf("a", "b"), vm.state.value.pedidos.map { it.from.userId },
            "a linha foi tirada de baixo do dedo")
    }

    /** Caminho de falha (C2): a consulta do push falha. */
    @Test
    fun `falha na consulta do push nao muda a tela nem mostra erro`() = runTest(dispatcher) {
        val (vm, fonte) = telaCom("a")
        fonte.pedidos = AppResult.Failure(AppError.Connection())

        vm.aoChegarPush(push)
        advanceUntilIdle()

        val s = vm.state.value
        assertEquals(listOf("a"), s.pedidos.map { it.from.userId })
        assertNull(s.aguardando)
        assertNull(s.erro, "um push não pode virar aviso de erro")
    }

    @Test
    fun `recarga completa descarta o que estava aguardando`() = runTest(dispatcher) {
        val (vm, fonte) = telaCom("a")
        fonte.pedidos = lista("a", "b")
        vm.aoChegarPush(push)
        advanceUntilIdle()

        vm.carregar()   // a pessoa saiu e voltou (ON_START)
        advanceUntilIdle()

        val s = vm.state.value
        assertEquals(listOf("a", "b"), s.pedidos.map { it.from.userId })
        assertNull(s.aguardando)
    }

    @Test
    fun `pedidosNovos conta so quem entrou`() {
        assertEquals(1, pedidosNovos(listOf(pedido("a")), listOf(pedido("a"), pedido("b"))))
        assertEquals(0, pedidosNovos(listOf(pedido("a"), pedido("b")), listOf(pedido("a"))))
        assertEquals(2, pedidosNovos(emptyList(), listOf(pedido("a"), pedido("b"))))
        assertTrue(pedidosNovos(emptyList(), emptyList()) == 0)
    }

    // ---- F.2: puxar para atualizar ----

    /**
     * [INVARIANTE] O pull liga `atualizando` e NÃO `carregando`, e troca a lista mesmo havendo
     * `aguardando`: o pull é um gesto da pessoa, ao contrário do push, que ela não pediu.
     */
    @Test
    fun `puxar para atualizar troca a lista e descarta o aguardando sem ligar carregando`() = runTest(dispatcher) {
        val (vm, fonte) = telaCom("a")
        fonte.pedidos = lista("a", "b")
        vm.aoChegarPush(push)
        advanceUntilIdle()
        assertEquals(1, vm.state.value.pedidosNovos)

        fonte.portao = CompletableDeferred()
        vm.atualizar()
        runCurrent()

        assertTrue(vm.state.value.atualizando)
        assertFalse(vm.state.value.carregando, "o spinner de tela cheia é só da primeira carga")

        fonte.portao!!.complete(Unit)
        advanceUntilIdle()

        val s = vm.state.value
        assertFalse(s.atualizando)
        assertEquals(listOf("a", "b"), s.pedidos.map { it.from.userId })
        assertNull(s.aguardando)
    }

    @Test
    fun `consumir o erro do pull limpa o snackbar`() = runTest(dispatcher) {
        val (vm, fonte) = telaCom("a")
        fonte.pedidos = AppResult.Failure(AppError.Connection())
        vm.atualizar()
        advanceUntilIdle()

        vm.consumirErroDoPull()

        assertNull(vm.state.value.erroDoPull, "senão o snackbar reaparece a cada recomposição")
    }

    @Test
    fun `falha ao puxar desliga atualizando e preserva a lista`() = runTest(dispatcher) {
        val (vm, fonte) = telaCom("a")

        fonte.pedidos = AppResult.Failure(AppError.Connection())
        vm.atualizar()
        advanceUntilIdle()

        val s = vm.state.value
        assertFalse(s.atualizando, "senão o indicador do pull gira para sempre")
        assertTrue(s.erroDoPull is AppError.Connection, "o pull offline precisa avisar (snackbar)")
        assertNull(s.erro, "o pull não vira erro permanente da tela")
        assertEquals(listOf("a"), s.pedidos.map { it.from.userId })
    }

    @Test
    fun `segundo pull durante um pull em curso e ignorado`() = runTest(dispatcher) {
        val (vm, fonte) = telaCom("a")
        val antes = fonte.consultasDePedidos

        fonte.portao = CompletableDeferred()
        vm.atualizar()
        runCurrent()
        vm.atualizar()
        runCurrent()
        fonte.portao!!.complete(Unit)
        advanceUntilIdle()

        assertEquals(antes + 1, fonte.consultasDePedidos)
    }
}
