package dev.rafael.features.program.presentation.viewmodel

import dev.rafael.core.result.AppError
import dev.rafael.core.result.AppResult
import dev.rafael.features.program.presentation.state.ProgramListEvent
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

@OptIn(ExperimentalCoroutinesApi::class)
class ProgramListViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest fun setup() { Dispatchers.setMain(dispatcher) }
    @AfterTest fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun `load popula os programas`() = runTest(dispatcher) {
        val repo = FakeProgramRepository(listResult = AppResult.Success(listOf(program("a"), program("b"))))
        val vm = ProgramListViewModel(repo)
        vm.onEvent(ProgramListEvent.Load)   // load é disparado pela tela (ON_RESUME), não pelo init
        advanceUntilIdle()

        assertFalse(vm.state.value.isLoading)
        assertEquals(2, vm.state.value.programs.size)
    }

    @Test
    fun `load com falha seta erro`() = runTest(dispatcher) {
        val repo = FakeProgramRepository(listResult = AppResult.Failure(AppError.NotFound()))
        val vm = ProgramListViewModel(repo)
        vm.onEvent(ProgramListEvent.Load)   // load é disparado pela tela (ON_RESUME), não pelo init
        advanceUntilIdle()

        assertEquals("Não encontrado", vm.state.value.error)
    }

    @Test
    fun `createManual seta createdId pra navegar`() = runTest(dispatcher) {
        val repo = FakeProgramRepository(createResult = AppResult.Success(program("new")))
        val vm = ProgramListViewModel(repo)
        advanceUntilIdle()

        vm.onEvent(ProgramListEvent.CreateManual("Meu programa"))
        advanceUntilIdle()

        assertEquals("new", vm.state.value.createdId)
    }

    @Test
    fun `createManual com nome vazio nao cria`() = runTest(dispatcher) {
        val vm = ProgramListViewModel(FakeProgramRepository())
        advanceUntilIdle()

        vm.onEvent(ProgramListEvent.CreateManual("   "))
        advanceUntilIdle()

        assertNull(vm.state.value.createdId)
        assertFalse(vm.state.value.isCreating)
    }

    @Test
    fun `consumeCreatedId limpa o evento`() = runTest(dispatcher) {
        val repo = FakeProgramRepository(createResult = AppResult.Success(program("new")))
        val vm = ProgramListViewModel(repo)
        advanceUntilIdle()
        vm.onEvent(ProgramListEvent.CreateManual("Meu programa"))
        advanceUntilIdle()

        vm.consumeCreatedId()

        assertNull(vm.state.value.createdId)
    }
}
