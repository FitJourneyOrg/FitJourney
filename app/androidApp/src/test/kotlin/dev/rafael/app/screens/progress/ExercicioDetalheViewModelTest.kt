package dev.rafael.app.screens.progress

import dev.rafael.contract.stats.ExercicioDetalheDto
import dev.rafael.contract.stats.PontoDeSessaoDto
import dev.rafael.core.result.AppError
import dev.rafael.core.result.AppResult
import dev.rafael.features.stats.domain.DetalheDeExercicio
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
 * Fake de uma interface de um metodo so -- nao ha motivo para reaproveitar os fakes grandes da
 * Home aqui (eles implementam interfaces com uma dezena de metodos que [DetalheDeExercicio] nem
 * tem).
 */
private class FakeDetalheDeExercicio : DetalheDeExercicio {
    var resultado: AppResult<ExercicioDetalheDto> = AppResult.Success(
        ExercicioDetalheDto(exerciseId = "ex-1", name = "Agachamento", points = emptyList()),
    )
    val chamadas = mutableListOf<Pair<String, String>>()

    override suspend fun buscar(programId: String, exercicioId: String): AppResult<ExercicioDetalheDto> {
        chamadas.add(programId to exercicioId)
        return resultado
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ExercicioDetalheViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest fun setup() { Dispatchers.setMain(dispatcher) }
    @AfterTest fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun `comeca carregando, antes de qualquer chamada`() {
        val vm = ExercicioDetalheViewModel(FakeDetalheDeExercicio())
        assertTrue(vm.state.value.carregando)
        assertNull(vm.state.value.detalhe)
        assertNull(vm.state.value.erro)
    }

    @Test
    fun `carregar com sucesso poe o detalhe no estado e tira o carregando`() = runTest(dispatcher) {
        val detalhes = FakeDetalheDeExercicio()
        detalhes.resultado = AppResult.Success(
            ExercicioDetalheDto(
                exerciseId = "ex-1",
                name = "Agachamento",
                points = listOf(
                    PontoDeSessaoDto(
                        date = "2026-09-01", weekNumber = 1, estimated1rm = 100.0,
                        volumeKg = 800.0, kg = 90.0, reps = 5, sets = 4,
                    ),
                ),
            ),
        )
        val vm = ExercicioDetalheViewModel(detalhes)

        vm.carregar("prog-1", "ex-1")
        advanceUntilIdle()

        assertFalse(vm.state.value.carregando)
        assertEquals("Agachamento", vm.state.value.detalhe?.name)
        assertEquals(1, vm.state.value.detalhe?.points?.size)
        assertNull(vm.state.value.erro)
        assertEquals(listOf("prog-1" to "ex-1"), detalhes.chamadas)
    }

    /**
     * Caminho de falha: o 403 do servidor (free sem entitlement) vira estado de erro, nao
     * excecao nem detalhe vazio silencioso -- a tela precisa saber MOSTRAR o paywall/erro.
     */
    @Test
    fun `carregar com erro poe o erro no estado e tira o carregando`() = runTest(dispatcher) {
        val detalhes = FakeDetalheDeExercicio()
        val erro = AppError.Forbidden("Assine o premium.", "ENTITLEMENT_REQUIRED")
        detalhes.resultado = AppResult.Failure(erro)
        val vm = ExercicioDetalheViewModel(detalhes)

        vm.carregar("prog-1", "ex-1")
        advanceUntilIdle()

        assertFalse(vm.state.value.carregando)
        assertEquals(erro, vm.state.value.erro)
        assertNull(vm.state.value.detalhe)
    }

    /**
     * Tocar em "ver detalhado" de novo (ex.: botao "tentar de novo" do `ErroDeTela`) tem de
     * voltar a carregar e LIMPAR o erro anterior -- sem isso a tela mostraria o erro antigo por
     * cima do spinner ate a resposta nova chegar.
     */
    @Test
    fun `carregar de novo depois de um erro limpa o erro e volta a carregar`() = runTest(dispatcher) {
        val detalhes = FakeDetalheDeExercicio()
        detalhes.resultado = AppResult.Failure(AppError.NotFound("Programa nao encontrado.", "PROGRAMA_NAO_EXISTE"))
        val vm = ExercicioDetalheViewModel(detalhes)
        vm.carregar("prog-1", "ex-1")
        advanceUntilIdle()
        assertTrue(vm.state.value.erro != null)

        detalhes.resultado = AppResult.Success(
            ExercicioDetalheDto(exerciseId = "ex-1", name = "Agachamento", points = emptyList()),
        )
        vm.carregar("prog-1", "ex-1")
        advanceUntilIdle()

        assertNull(vm.state.value.erro)
        assertEquals("Agachamento", vm.state.value.detalhe?.name)
    }
}
