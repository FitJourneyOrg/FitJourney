package dev.rafael.app.screens.progress

import dev.rafael.app.screens.home.FakeHistorico
import dev.rafael.app.screens.home.FakeStats
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

/**
 * G.6 -- "arraste para atualizar" na tela de Progresso. Reusa os fakes da Home
 * ([FakeHistorico]/[FakeStats]): mesma interface ([dev.rafael.features.session.domain.HistoricoDeSessoes],
 * [dev.rafael.features.stats.domain.Stats]), mesmo comportamento simulado -- não faz sentido duplicar.
 *
 * Sem teste de caminho de falha aqui de propósito: as duas interfaces são documentadas como
 * "nunca falha" / "falha em silêncio" (o `AppResult`/erro já é engolido por dentro da
 * implementação real). Não existe um branch de erro no `ProgressViewModel` pra exercitar --
 * forçar uma exceção nos fakes só pra ter "um caminho de falha" testaria um cenário que o
 * contrato da interface promete que não acontece.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ProgressViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest fun setup() { Dispatchers.setMain(dispatcher) }
    @AfterTest fun tearDown() { Dispatchers.resetMain() }

    /**
     * ⭐ O `flush` ficou aqui mesmo depois do histórico sair da tela (desmembramento de
     * 2026-10-01), e este teste é o que impede alguém de "limpar" essa dependência que parece
     * sobra: as métricas são derivadas das sessões no SERVIDOR, então subir o treino feito
     * offline tem de acontecer ANTES de pedir os números.
     *
     * Sem ele, terminar o segundo treino offline e abrir o Progresso mostraria "1 treino".
     */
    @Test
    fun `sincronizar sobe as sessoes pendentes antes de pedir as metricas`() = runTest(dispatcher) {
        val historico = FakeHistorico()
        val stats = FakeStats()
        val vm = ProgressViewModel(historico, stats)
        advanceUntilIdle()   // consome o sync automático do init

        vm.sincronizar()   // o gesto de arraste chama isto
        advanceUntilIdle()

        assertEquals(2, historico.flushes, "parou de subir o que foi feito offline")
        assertEquals(2, stats.sincronizacoes)
        assertEquals(
            0, historico.sincronizacoes,
            "o Progresso não mostra mais o histórico -- baixá-lo aqui é requisição sem tela",
        )
    }

    @Test
    fun `sincronizando volta a false ao terminar (indicador nao fica preso girando)`() = runTest(dispatcher) {
        val vm = ProgressViewModel(FakeHistorico(), FakeStats())
        advanceUntilIdle()

        assertFalse(vm.state.value.sincronizando)

        vm.sincronizar()
        advanceUntilIdle()

        assertFalse(vm.state.value.sincronizando)
    }
}
