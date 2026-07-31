package dev.rafael.features.workout.presentation.viewmodel

import dev.rafael.core.catalog.ExerciseLookup
import dev.rafael.core.catalog.ExerciseRef
import dev.rafael.core.result.AppError
import dev.rafael.core.result.AppResult
import dev.rafael.features.workout.domain.model.Workout
import dev.rafael.features.workout.domain.model.WorkoutExercise
import dev.rafael.features.workout.domain.model.WorkoutSet
import dev.rafael.features.workout.domain.model.WorkoutSummary
import dev.rafael.features.workout.domain.repository.WorkoutRepository
import dev.rafael.features.workout.presentation.state.WorkoutDetailEvent
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
 * Testa o WorkoutDetailViewModel — máquina de estado. Usa runTest + Dispatchers.setMain
 * pra controlar o viewModelScope (o VM roda load() no init e lança coroutines).
 * Prova o núcleo do ARCH #25: 403 ENTITLEMENT ao editar programa IA abre o paywall.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WorkoutDetailViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest fun setup() { Dispatchers.setMain(dispatcher) }
    @AfterTest fun tearDown() { Dispatchers.resetMain() }

    // ---- fakes ----
    private class FakeRepo(
        private val getResult: AppResult<Workout>,
        private val updateResult: AppResult<Workout>? = null,
        private val deleteResult: AppResult<Unit> = AppResult.Success(Unit),
    ) : WorkoutRepository {
        var updateCalls = 0
        override suspend fun list() = AppResult.Success(emptyList<WorkoutSummary>())
        override suspend fun get(id: String) = getResult
        override suspend fun create(workout: Workout) = getResult
        override suspend fun update(id: String, workout: Workout): AppResult<Workout> {
            updateCalls++
            return updateResult ?: getResult
        }
        override suspend fun delete(id: String) = deleteResult
    }

    private class FakeLookup(private val refs: Map<String, ExerciseRef> = emptyMap()) : ExerciseLookup {
        override suspend fun byIds(ids: List<String>) = refs
    }

    private fun workout(n: Int) = Workout(
        id = "w1", name = "Upper", programId = "prog-1",
        exercises = (0 until n).map {
            WorkoutExercise(
                exerciseId = "ex-$it", orderIndex = it, restSeconds = 90, rir = 2,
                sets = listOf(WorkoutSet(reps = 10, orderIndex = 0)),
            )
        },
        createdAt = null, updatedAt = null,
    )

    private val entitlement = AppResult.Failure(AppError.Forbidden(code = "ENTITLEMENT_REQUIRED"))

    // ---- tests ----

    @Test
    fun `load preenche nome e exercicios`() = runTest(dispatcher) {
        val vm = WorkoutDetailViewModel("w1", FakeRepo(AppResult.Success(workout(2))), FakeLookup())
        advanceUntilIdle()

        val s = vm.state.value
        assertFalse(s.isLoading)
        assertEquals("Upper", s.name)
        assertEquals(2, s.exercises.size)
    }

    @Test
    fun `load com falha seta erro`() = runTest(dispatcher) {
        val vm = WorkoutDetailViewModel("w1", FakeRepo(AppResult.Failure(AppError.NotFound())), FakeLookup())
        advanceUntilIdle()

        assertFalse(vm.state.value.isLoading)
        assertEquals("Não encontrado", vm.state.value.error)
    }

    @Test
    fun `trocar exercicio em programa IA sem premium abre paywall`() = runTest(dispatcher) {
        val repo = FakeRepo(getResult = AppResult.Success(workout(3)), updateResult = entitlement)
        val vm = WorkoutDetailViewModel("w1", repo, FakeLookup())
        advanceUntilIdle()

        vm.onEvent(WorkoutDetailEvent.SwapExercise(orderIndex = 0, newExerciseId = "novo"))
        advanceUntilIdle()

        assertTrue(vm.state.value.showPaywall, "403 ENTITLEMENT deveria abrir o paywall")
    }

    @Test
    fun `dismiss fecha o paywall`() = runTest(dispatcher) {
        val repo = FakeRepo(getResult = AppResult.Success(workout(3)), updateResult = entitlement)
        val vm = WorkoutDetailViewModel("w1", repo, FakeLookup())
        advanceUntilIdle()
        vm.onEvent(WorkoutDetailEvent.SwapExercise(0, "novo"))
        advanceUntilIdle()

        vm.onEvent(WorkoutDetailEvent.DismissPaywall)
        assertFalse(vm.state.value.showPaywall)
    }

    @Test
    fun `remover com so um exercicio bloqueia e nao chama update`() = runTest(dispatcher) {
        val repo = FakeRepo(getResult = AppResult.Success(workout(1)))
        val vm = WorkoutDetailViewModel("w1", repo, FakeLookup())
        advanceUntilIdle()

        vm.onEvent(WorkoutDetailEvent.RemoveExercise(0))
        advanceUntilIdle()

        assertEquals(0, repo.updateCalls, "não deveria salvar com 1 exercício")
        assertTrue(vm.state.value.error?.contains("ao menos 1") == true)
    }

    @Test
    fun `delete com sucesso marca isDeleted`() = runTest(dispatcher) {
        val repo = FakeRepo(getResult = AppResult.Success(workout(2)))
        val vm = WorkoutDetailViewModel("w1", repo, FakeLookup())
        advanceUntilIdle()

        vm.onEvent(WorkoutDetailEvent.Delete)
        advanceUntilIdle()

        assertTrue(vm.state.value.isDeleted)
    }
}
