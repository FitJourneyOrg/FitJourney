package dev.rafael.app.screens.historico

import dev.rafael.app.screens.home.FakeHistorico
import dev.rafael.app.screens.home.sessao
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
import kotlin.test.assertTrue

/**
 * O histórico em tela própria (desmembramento de 2026-10-01). Reusa o [FakeHistorico] da Home:
 * mesma interface, mesmo comportamento simulado.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HistoricoViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest fun setup() { Dispatchers.setMain(dispatcher) }
    @AfterTest fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun `sobe o pendente e baixa o que falta`() = runTest(dispatcher) {
        val historico = FakeHistorico()
        val vm = HistoricoViewModel(historico)
        advanceUntilIdle()   // consome o sync do init

        vm.sincronizar()     // o gesto de arraste
        advanceUntilIdle()

        assertEquals(2, historico.flushes, "1 do init + 1 do arraste")
        assertEquals(2, historico.sincronizacoes)
    }

    @Test
    fun `a lista vem do banco local e re-emite sozinha`() = runTest(dispatcher) {
        val historico = FakeHistorico()
        val vm = HistoricoViewModel(historico)
        advanceUntilIdle()

        assertTrue(vm.state.value.sessoes.isEmpty())
        assertFalse(vm.state.value.carregandoInicial, "o Flow já emitiu, mesmo vazio")

        // É assim que o sync grava: a tela não pede de novo, ela observa.
        historico.historico.value = listOf(sessao(finishedAt = "2026-10-01T10:00:00", pendente = true))
        advanceUntilIdle()

        assertEquals(1, vm.state.value.sessoes.size)
    }

    @Test
    fun `sincronizando volta a false ao terminar (indicador nao fica preso girando)`() =
        runTest(dispatcher) {
            val vm = HistoricoViewModel(FakeHistorico())
            advanceUntilIdle()
            assertFalse(vm.state.value.sincronizando)

            vm.sincronizar()
            advanceUntilIdle()

            assertFalse(vm.state.value.sincronizando)
        }
}
