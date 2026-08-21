package dev.rafael.app.screens.menu

import dev.rafael.app.data.me.Me
import dev.rafael.app.data.stats.Stats
import dev.rafael.contract.stats.UserStatsDto
import dev.rafael.contract.user.UserDto
import dev.rafael.core.result.AppResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flatMapLatest
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
        /** Quem está logado AGORA. Nulo = ninguém (estado do app antes do login). */
        var uid: String? = null
        private val porUsuario = MutableStateFlow(
            mapOf("u1" to UserDto(id = "u1", displayName = "Rafael", email = null)),
        )
        var sincronizacoes = 0

        // Espelha o repositório: a chave é resolvida NA COLETA, não a cada emissão.
        override fun observar(): Flow<UserDto?> =
            flow { emit(uid) }.flatMapLatest { u -> porUsuario.let { m -> MutableStateFlow(m.value[u]) } }

        override suspend fun sincronizar(forcar: Boolean) { sincronizacoes++ }
        override suspend fun renomear(nome: String): AppResult<String> = AppResult.Success(nome)
    }

    private class FakeStats : Stats {
        val valores = MutableStateFlow<UserStatsDto?>(null)
        override fun observar(): Flow<UserStatsDto?> = valores
        override suspend fun sincronizar(forcar: Boolean) = Unit
    }

    @Test
    fun `abrir o menu depois do login mostra o nome, e nao o placeholder`() = runTest(dispatcher) {
        val me = FakeMe()                       // ninguém logado ainda
        val viewModel = MenuViewModel(me, FakeStats())

        // `stateIn(..., WhileSubscribed)` só liga o upstream quando ALGUÉM coleta. Na tela quem
        // faz isso é o `collectAsStateWithLifecycle`; aqui tem de ser explícito, senão o
        // `state.value` fica eternamente no valor inicial e o teste "passa" sem exercitar nada.
        backgroundScope.launch { viewModel.state.collect { } }
        advanceUntilIdle()

        assertEquals("", viewModel.state.value.nome, "antes do login não há nome — é o esperado")

        me.uid = "u1"                           // login acontece
        viewModel.aoAbrir()                     // usuário abre o menu
        advanceUntilIdle()

        assertEquals(
            "Rafael",
            viewModel.state.value.nome,
            "abrir o menu tem de REINICIAR a coleta; sem isso a chave do cache fica no uid nulo",
        )
    }

    @Test
    fun `abrir o menu pede sync`() = runTest(dispatcher) {
        val me = FakeMe()
        val viewModel = MenuViewModel(me, FakeStats())
        advanceUntilIdle()

        viewModel.aoAbrir()
        advanceUntilIdle()

        assertEquals(1, me.sincronizacoes, "o TTL decide se vai à rede — aqui só se pede")
    }
}
