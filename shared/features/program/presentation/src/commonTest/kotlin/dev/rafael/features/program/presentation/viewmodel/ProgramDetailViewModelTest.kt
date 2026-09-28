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
import kotlin.test.assertIs
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
        assertEquals("Programa não encontrado", vm.state.value.error?.message)
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

    @Test
    fun `pendencia de sync aparece pelo alvoId e some quando a fila esvazia`() = runTest(dispatcher) {
        val repo = FakeProgramRepository(listResult = AppResult.Success(listOf(program("p1"))))
        val vm = ProgramDetailViewModel("p1", repo)
        advanceUntilIdle()

        repo.pendentes.value = setOf(
            dev.rafael.features.program.domain.model.PendenciaDeSync(alvoId = "w-1"),
        )
        advanceUntilIdle()
        assertEquals("w-1", vm.state.value.pendenciaDe("w-1")?.alvoId)

        repo.pendentes.value = emptySet()
        advanceUntilIdle()
        assertNull(vm.state.value.pendenciaDe("w-1"))
    }

    // ---- descartar pendência permanente do outbox ----

    @Test
    fun `descartar chama o repositorio e recarrega o programa`() = runTest(dispatcher) {
        val repo = FakeProgramRepository(listResult = AppResult.Success(listOf(program("p1"))))
        val vm = ProgramDetailViewModel("p1", repo)
        vm.onEvent(ProgramDetailEvent.Retry)
        advanceUntilIdle()

        vm.onEvent(ProgramDetailEvent.Descartar("w-1"))
        advanceUntilIdle()

        assertEquals("w-1", repo.descartarCalledWith)
        assertEquals("p1", vm.state.value.program?.id)   // recarregou (load forçado), não sumiu
    }

    @Test
    fun `descartar com o refresh seguinte falhando ainda assim propaga o erro`() = runTest(dispatcher) {
        val repo = FakeProgramRepository(listResult = AppResult.Failure(AppError.Connection()))
        val vm = ProgramDetailViewModel("p1", repo)
        advanceUntilIdle()

        vm.onEvent(ProgramDetailEvent.Descartar("w-1"))
        advanceUntilIdle()

        assertEquals("w-1", repo.descartarCalledWith)
        assertIs<AppError.Connection>(vm.state.value.error)
    }
}
