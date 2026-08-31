package dev.rafael.app.screens.menu

import dev.rafael.app.data.me.Me
import dev.rafael.app.data.sessao.SairDaConta
import dev.rafael.app.data.stats.Stats
import dev.rafael.core.network.TokenProvider
import dev.rafael.app.screens.home.FakeAuth
import dev.rafael.app.screens.home.FakePerfil
import dev.rafael.contract.stats.UserStatsDto
import dev.rafael.contract.user.UserDto
import dev.rafael.core.result.AppResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
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
import kotlin.test.assertTrue

/**
 * REGRESSÃO do "?" eterno no cabeçalho do menu (encontrado na bateria manual da A.0).
 *
 * O drawer entra em composição junto com o `AppNavHost` — antes do login. `MeRepository.observar()`
 * resolve a chave do cache (`me:<uid>`) no INÍCIO da coleta, então um VM criado cedo demais fica
 * preso à chave do uid nulo. Como este VM vive enquanto a Activity viver, ele nunca se corrigia:
 * o menu mostrava "?" / "Você" enquanto a tela de perfil, com VM criado depois do login, mostrava
 * o nome certo.
 *
 * O [FakeMe] abaixo REPRODUZ essa mecânica de propósito — resolve o uid na coleta, como o
 * repositório real. Um fake que só devolvesse um `StateFlow` passaria mesmo com o bug de volta,
 * e o teste não valeria nada.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MenuViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest fun setup() { Dispatchers.setMain(dispatcher) }
    @AfterTest fun tearDown() { Dispatchers.resetMain() }

    private class FakeMe : Me {
        /**
         * A SESSÃO como fluxo, espelhando `TokenProvider.uidFlow()`. Nulo = ninguém logado,
         * que é o estado do app quando este ViewModel nasce.
         */
        val uid = MutableStateFlow<String?>(null)
        private val porUsuario = mapOf("u1" to UserDto(id = "u1", displayName = "Rafael", email = null))
        var sincronizacoes = 0

        // Espelha o repositório REAL: a chave sai do fluxo de uid, então re-chaveia sozinha.
        override fun observar(): Flow<UserDto?> = uid.map { porUsuario[it] }

        override suspend fun sincronizar(forcar: Boolean) { sincronizacoes++ }
        override suspend fun renomear(nome: String): AppResult<String> = AppResult.Success(nome)
    }

    /** O logout não é o assunto destes testes — reusa os dublês da Home. */
    private fun sair() = SairDaConta(FakeAuth(), FakePerfil()) { /* baixa do push: F.1 */ }

    /** Compartilha o MESMO fluxo de uid do [FakeMe]: no app real, a fonte também é uma só. */
    private class FakeToken(private val uid: MutableStateFlow<String?>) : TokenProvider {
        override suspend fun currentToken(): String? = uid.value?.let { "token-$it" }
        override suspend fun currentUid(): String? = uid.value
        override fun uidFlow(): Flow<String?> = uid
    }

    private class FakeStats : Stats {
        val valores = MutableStateFlow<UserStatsDto?>(null)
        override fun observar(): Flow<UserStatsDto?> = valores
        override suspend fun sincronizar(forcar: Boolean) = Unit
    }

    @Test
    fun `o nome aparece no LOGIN, sem ninguem precisar pedir`() = runTest(dispatcher) {
        // REGRESSÃO do "?" eterno. A primeira correção fazia o menu reiniciar a coleta ao abrir
        // — funcionava, mas era contorno: dependia de o usuário abrir o menu, e deixava
        // `Stats`, `Achievements` e `Groups` com o mesmo defeito latente. A correção de raiz é
        // o `TokenProvider.uidFlow()`, e este teste cobra isso: NINGUÉM chama nada aqui além
        // de logar.
        val me = FakeMe()                       // ninguém logado ainda
        val viewModel = MenuViewModel(me, FakeStats(), sair(), FakeToken(me.uid))

        // `stateIn(..., WhileSubscribed)` só liga o upstream quando ALGUÉM coleta. Na tela quem
        // faz isso é o `collectAsStateWithLifecycle`; aqui tem de ser explícito, senão o
        // `state.value` fica eternamente no valor inicial e o teste "passa" sem exercitar nada.
        backgroundScope.launch { viewModel.state.collect { } }
        advanceUntilIdle()

        assertEquals("", viewModel.state.value.nome, "antes do login não há nome — é o esperado")

        me.uid.value = "u1"                     // login acontece, e só isso
        advanceUntilIdle()

        assertEquals(
            "Rafael",
            viewModel.state.value.nome,
            "a chave do cache tem de acompanhar a SESSÃO, não o instante em que alguém coletou",
        )
    }

    @Test
    fun `trocar de conta troca o que a tela mostra`() = runTest(dispatcher) {
        // O outro lado da mesma moeda: sair não pode deixar o nome do usuário anterior na tela.
        val me = FakeMe()
        val viewModel = MenuViewModel(me, FakeStats(), sair(), FakeToken(me.uid))
        backgroundScope.launch { viewModel.state.collect { } }
        me.uid.value = "u1"
        advanceUntilIdle()
        assertEquals("Rafael", viewModel.state.value.nome)

        me.uid.value = null                     // logout
        advanceUntilIdle()

        assertEquals("", viewModel.state.value.nome, "o nome da conta anterior não pode sobrar")
    }

    @Test
    fun `sair DUAS vezes dispara DUAS vezes`() = runTest(dispatcher) {
        // REGRESSÃO: este ViewModel vive enquanto a Activity viver e atravessa logout e login
        // sem ser recriado. `saiu` é EVENTO modelado como estado — sem consumo, ficava `true`
        // para sempre depois do primeiro logout, o `LaunchedEffect(saiu)` da tela não
        // redisparava, e o segundo "Sair" simplesmente não fazia nada: menu travado aberto.
        val me = FakeMe()
        val viewModel = MenuViewModel(me, FakeStats(), sair(), FakeToken(me.uid))
        backgroundScope.launch { viewModel.saiu.collect { } }

        viewModel.sair()
        advanceUntilIdle()
        assertTrue(viewModel.saiu.value, "primeira saída")

        viewModel.consumirSaida()               // a tela navegou e consumiu
        assertFalse(viewModel.saiu.value)

        viewModel.sair()                        // login de outra conta, e sair de novo
        advanceUntilIdle()
        assertTrue(viewModel.saiu.value, "a segunda saída também tem de disparar")
    }

    @Test
    fun `abrir o menu pede sync`() = runTest(dispatcher) {
        // Continua valendo: a chave se resolve sozinha, mas alguém tem de pedir o dado NOVO.
        val me = FakeMe()
        val viewModel = MenuViewModel(me, FakeStats(), sair(), FakeToken(me.uid))
        advanceUntilIdle()

        viewModel.aoAbrir()
        advanceUntilIdle()

        assertEquals(1, me.sincronizacoes, "o TTL decide se vai à rede — aqui só se pede")
    }
}
