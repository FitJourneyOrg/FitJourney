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
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

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

    // ---- ARCH #30: falha de SYNC ≠ erro de tela ----

    @Test
    fun `falha de sync nao vira erro vermelho`() = runTest(dispatcher) {
        val repo = FakeProgramRepository(listResult = AppResult.Failure(AppError.Connection()))
        val vm = ProgramListViewModel(repo)
        vm.onEvent(ProgramListEvent.Load)
        advanceUntilIdle()

        assertNull(vm.state.value.error)                  // `error` é só pra erro de AÇÃO
        assertIs<AppError.Connection>(vm.state.value.erroSync)   // tipo, não texto: quem escreve o texto é a UI
    }

    @Test
    fun `sem dado local e sync falhou mostra sem conexao`() = runTest(dispatcher) {
        val repo = FakeProgramRepository(listResult = AppResult.Failure(AppError.Connection()))
        val vm = ProgramListViewModel(repo)                // local vazio: aparelho novo, offline
        vm.onEvent(ProgramListEvent.Load)
        advanceUntilIdle()

        assertTrue(vm.state.value.vazioPorFaltaDeSync)
        assertFalse(vm.state.value.isLoading)              // não pode ficar em shimmer eterno
    }

    @Test
    fun `com dado local a falha de sync e invisivel`() = runTest(dispatcher) {
        val repo = FakeProgramRepository(listResult = AppResult.Failure(AppError.Connection()))
        repo.local.value = listOf(program("a"), program("b"))   // já sincronizou antes
        val vm = ProgramListViewModel(repo)
        vm.onEvent(ProgramListEvent.Load)
        advanceUntilIdle()

        assertEquals(2, vm.state.value.programs.size)      // offline funciona igual
        assertFalse(vm.state.value.vazioPorFaltaDeSync)
        assertNull(vm.state.value.error)
    }

    @Test
    fun `vazio de verdade nao vira sem conexao`() = runTest(dispatcher) {
        val repo = FakeProgramRepository(listResult = AppResult.Success(emptyList()))
        val vm = ProgramListViewModel(repo)                // sync OK, usuário não tem programa
        vm.onEvent(ProgramListEvent.Load)
        advanceUntilIdle()

        assertFalse(vm.state.value.vazioPorFaltaDeSync)    // → "Nenhum programa ainda."
        assertTrue(vm.state.value.sincronizouAlgumaVez)
    }

    @Test
    fun `retry com sucesso tira o sem conexao`() = runTest(dispatcher) {
        val repo = FakeProgramRepository(listResult = AppResult.Failure(AppError.Connection()))
        val vm = ProgramListViewModel(repo)
        vm.onEvent(ProgramListEvent.Load)
        advanceUntilIdle()
        assertTrue(vm.state.value.vazioPorFaltaDeSync)

        repo.listResult = AppResult.Success(listOf(program("a")))   // rede voltou
        vm.onEvent(ProgramListEvent.Retry)
        advanceUntilIdle()

        assertFalse(vm.state.value.vazioPorFaltaDeSync)
        assertEquals(1, vm.state.value.programs.size)
    }

    @Test
    fun `sync que grava no local re-emite pra tela`() = runTest(dispatcher) {
        val repo = FakeProgramRepository(listResult = AppResult.Failure(AppError.Connection()))
        val vm = ProgramListViewModel(repo)
        advanceUntilIdle()
        assertEquals(0, vm.state.value.programs.size)

        repo.local.value = listOf(program("a"))   // SyncWorker gravou em background
        advanceUntilIdle()

        assertEquals(1, vm.state.value.programs.size)   // tela atualiza sozinha, sem load()
    }

    @Test
    fun `erro de acao continua vermelho`() = runTest(dispatcher) {
        val repo = FakeProgramRepository(createResult = AppResult.Failure(AppError.Connection()))
        val vm = ProgramListViewModel(repo)
        advanceUntilIdle()

        vm.onEvent(ProgramListEvent.CreateManual("Meu programa"))
        advanceUntilIdle()

        assertIs<AppError.Connection>(vm.state.value.error)      // aqui SIM: o usuário pediu algo
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
