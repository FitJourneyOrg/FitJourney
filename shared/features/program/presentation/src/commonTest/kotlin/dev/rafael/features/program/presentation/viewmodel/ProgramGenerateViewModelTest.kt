package dev.rafael.features.program.presentation.viewmodel

import dev.rafael.core.result.AppError
import dev.rafael.core.result.AppResult
import dev.rafael.features.program.presentation.state.GenerateError
import dev.rafael.features.program.presentation.state.ProgramGenerateEvent
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
class ProgramGenerateViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest fun setup() { Dispatchers.setMain(dispatcher) }
    @AfterTest fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun `generate sucesso seta generatedId`() = runTest(dispatcher) {
        val repo = FakeProgramRepository(generateResult = AppResult.Success(program("g1")))
        val vm = ProgramGenerateViewModel(repo)

        vm.onEvent(ProgramGenerateEvent.Generate)
        advanceUntilIdle()

        assertEquals("g1", vm.state.value.generatedId)
    }

    @Test
    fun `403 entitlement mapeia pra erro Entitlement (paywall)`() = runTest(dispatcher) {
        val repo = FakeProgramRepository(
            generateResult = AppResult.Failure(AppError.Forbidden(code = "ENTITLEMENT_REQUIRED")),
        )
        val vm = ProgramGenerateViewModel(repo)

        vm.onEvent(ProgramGenerateEvent.Generate)
        advanceUntilIdle()

        assertEquals(GenerateError.Entitlement, vm.state.value.error)
    }

    @Test
    fun `403 health mapeia pra HealthGate (PAR-Q)`() = runTest(dispatcher) {
        val repo = FakeProgramRepository(
            generateResult = AppResult.Failure(AppError.Forbidden(code = "HEALTH_GATE_REQUIRED")),
        )
        val vm = ProgramGenerateViewModel(repo)

        vm.onEvent(ProgramGenerateEvent.Generate)
        advanceUntilIdle()

        assertEquals(GenerateError.HealthGate, vm.state.value.error)
    }

    @Test
    fun `outro erro vira GenerateError Other`() = runTest(dispatcher) {
        val repo = FakeProgramRepository(generateResult = AppResult.Failure(AppError.NotFound()))
        val vm = ProgramGenerateViewModel(repo)

        vm.onEvent(ProgramGenerateEvent.Generate)
        advanceUntilIdle()

        assertTrue(vm.state.value.error is GenerateError.Other)
    }

    @Test
    fun `dismiss limpa o erro`() = runTest(dispatcher) {
        val repo = FakeProgramRepository(generateResult = AppResult.Failure(AppError.NotFound()))
        val vm = ProgramGenerateViewModel(repo)
        vm.onEvent(ProgramGenerateEvent.Generate)
        advanceUntilIdle()

        vm.onEvent(ProgramGenerateEvent.DismissError)

        assertNull(vm.state.value.error)
    }
}
