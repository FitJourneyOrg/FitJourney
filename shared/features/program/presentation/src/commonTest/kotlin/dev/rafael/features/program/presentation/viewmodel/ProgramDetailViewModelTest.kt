package dev.rafael.features.program.presentation.viewmodel

import dev.rafael.core.result.AppError
import dev.rafael.core.result.AppResult
import dev.rafael.features.program.presentation.state.ProgramDetailEvent
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ProgramDetailViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest fun setup() { Dispatchers.setMain(dispatcher) }
    @AfterTest fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun `load acha o programa pelo id (nao existe GET por id, filtra da lista)`() = runTest(dispatcher) {
        val repo = FakeProgramRepository(
            listResult = AppResult.Success(listOf(program("p1", "A"), program("p2", "B"))),
        )
        val vm = ProgramDetailViewModel("p1", repo)
        vm.onEvent(ProgramDetailEvent.Retry)   // load vem da tela (ON_RESUME), não do init
        advanceUntilIdle()

        assertEquals("p1", vm.state.value.program?.id)
    }

    @Test
    fun `load sem achar o id vira erro`() = runTest(dispatcher) {
        val repo = FakeProgramRepository(listResult = AppResult.Success(listOf(program("outro"))))
        val vm = ProgramDetailViewModel("p1", repo)
        vm.onEvent(ProgramDetailEvent.Retry)   // load vem da tela (ON_RESUME), não do init
        advanceUntilIdle()

        assertNull(vm.state.value.program)
        assertEquals("Programa não encontrado", vm.state.value.error)
    }

    @Test
    fun `rename atualiza o programa`() = runTest(dispatcher) {
        val repo = FakeProgramRepository(
            listResult = AppResult.Success(listOf(program("p1"))),
            renameResult = AppResult.Success(program("p1", "Novo nome")),
        )
        val vm = ProgramDetailViewModel("p1", repo)
        vm.onEvent(ProgramDetailEvent.Retry)   // load vem da tela (ON_RESUME), não do init
        advanceUntilIdle()

        vm.onEvent(ProgramDetailEvent.Rename("Novo nome"))
        advanceUntilIdle()

        assertEquals("Novo nome", vm.state.value.program?.name)
    }

    @Test
    fun `delete com sucesso marca isDeleted`() = runTest(dispatcher) {
        val repo = FakeProgramRepository(listResult = AppResult.Success(listOf(program("p1"))))
        val vm = ProgramDetailViewModel("p1", repo)
        vm.onEvent(ProgramDetailEvent.Retry)   // load vem da tela (ON_RESUME), não do init
        advanceUntilIdle()

        vm.onEvent(ProgramDetailEvent.Delete)
        advanceUntilIdle()

        assertTrue(vm.state.value.isDeleted)
    }
}
