package dev.rafael.features.exercise.presentation.viewmodel

import dev.rafael.contract.exercise.ExerciseCategory
import dev.rafael.contract.profile.MuscleGroup
import dev.rafael.core.result.AppError
import dev.rafael.core.result.AppResult
import dev.rafael.features.exercise.domain.model.Exercise
import dev.rafael.features.exercise.domain.repository.ExerciseRepository
import dev.rafael.features.exercise.presentation.state.ExerciseListEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
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

/**
 * A parte nova: os dois filtros (categoria e músculo) COEXISTEM e o ViewModel precisa
 * re-observar com os dois juntos a cada evento, não só com o que acabou de mudar.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ExerciseListViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest fun setup() { Dispatchers.setMain(dispatcher) }
    @AfterTest fun tearDown() { Dispatchers.resetMain() }

    private fun exercise(id: String) = Exercise(
        id = id, name = id, category = ExerciseCategory.CHEST,
        description = null, videoRef = "v.mp4", thumbRef = "t.png",
        primaryMuscles = emptyList(), secondaryMuscles = emptyList(),
        equipment = null, movementPattern = null,
        isCompound = null, unilateral = null, prescriptionType = null, level = null,
    )

    /** Registra os pares (category, muscleGroup) com que foi observado, na ordem em que chegaram. */
    private inner class FakeRepo(
        private val refreshResult: AppResult<Unit> = AppResult.Success(Unit),
    ) : ExerciseRepository {
        val chamadas = mutableListOf<Pair<ExerciseCategory?, MuscleGroup?>>()

        override fun observeExercises(category: ExerciseCategory?, muscleGroup: MuscleGroup?): Flow<List<Exercise>> {
            chamadas += category to muscleGroup
            return flowOf(if (muscleGroup == null) listOf(exercise("ex-1")) else emptyList())
        }

        override suspend fun refresh(forcar: Boolean): AppResult<Unit> = refreshResult
        override suspend fun alternatives(exerciseId: String): AppResult<List<Exercise>> = AppResult.Success(emptyList())
        override suspend fun getDetail(exerciseId: String): AppResult<Exercise> = AppResult.Failure(AppError.NotFound())
    }

    @Test
    fun `no boot observa sem nenhum filtro`() = runTest(dispatcher) {
        val repo = FakeRepo()
        ExerciseListViewModel(repo)
        advanceUntilIdle()

        assertEquals(listOf<Pair<ExerciseCategory?, MuscleGroup?>>(null to null), repo.chamadas)
    }

    @Test
    fun `selecionar musculo mantem a categoria ja escolhida`() = runTest(dispatcher) {
        val repo = FakeRepo()
        val vm = ExerciseListViewModel(repo)
        advanceUntilIdle()

        vm.onEvent(ExerciseListEvent.CategorySelected(ExerciseCategory.LEGS))
        advanceUntilIdle()
        vm.onEvent(ExerciseListEvent.MuscleGroupSelected(MuscleGroup.GLUTES))
        advanceUntilIdle()

        assertEquals(ExerciseCategory.LEGS to MuscleGroup.GLUTES, repo.chamadas.last())
        assertEquals(MuscleGroup.GLUTES, vm.state.value.selectedMuscleGroup)
        assertEquals(ExerciseCategory.LEGS, vm.state.value.selectedCategory)
    }

    @Test
    fun `selecionar categoria mantem o musculo ja escolhido`() = runTest(dispatcher) {
        val repo = FakeRepo()
        val vm = ExerciseListViewModel(repo)
        advanceUntilIdle()

        vm.onEvent(ExerciseListEvent.MuscleGroupSelected(MuscleGroup.CORE))
        advanceUntilIdle()
        vm.onEvent(ExerciseListEvent.CategorySelected(ExerciseCategory.FUNCTIONAL_HIT))
        advanceUntilIdle()

        assertEquals(ExerciseCategory.FUNCTIONAL_HIT to MuscleGroup.CORE, repo.chamadas.last())
    }

    @Test
    fun `desmarcar o musculo (null) volta a mostrar a lista sem filtrar por musculo`() = runTest(dispatcher) {
        val repo = FakeRepo()
        val vm = ExerciseListViewModel(repo)
        advanceUntilIdle()

        vm.onEvent(ExerciseListEvent.MuscleGroupSelected(MuscleGroup.BICEPS))
        advanceUntilIdle()
        assertEquals(emptyList(), vm.state.value.exercises)   // FakeRepo devolve vazio com filtro != null

        vm.onEvent(ExerciseListEvent.MuscleGroupSelected(null))
        advanceUntilIdle()

        assertNull(vm.state.value.selectedMuscleGroup)
        assertEquals(listOf("ex-1"), vm.state.value.exercises.map { it.id })
    }

    @Test
    fun `falha no refresh nao apaga o filtro de musculo ja selecionado`() = runTest(dispatcher) {
        val repo = FakeRepo(refreshResult = AppResult.Failure(AppError.Connection()))
        val vm = ExerciseListViewModel(repo)
        advanceUntilIdle()

        vm.onEvent(ExerciseListEvent.MuscleGroupSelected(MuscleGroup.SHOULDERS))
        advanceUntilIdle()

        assertIs<AppError.Connection>(vm.state.value.error)
        assertEquals(MuscleGroup.SHOULDERS, vm.state.value.selectedMuscleGroup)
    }
}
