package dev.rafael.features.exercise.presentation.viewmodel

import dev.rafael.contract.exercise.ExerciseCategory
import dev.rafael.contract.profile.MuscleGroup
import dev.rafael.core.result.AppError
import dev.rafael.core.result.AppResult
import dev.rafael.features.exercise.domain.model.Exercise
import dev.rafael.features.exercise.domain.model.FiltroDeExercicios
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
 * Dois comportamentos sob teste aqui:
 *
 * 1. Os eixos de filtro **coexistem** — o ViewModel re-observa com todos juntos a cada evento, e
 *    não só com o que acabou de mudar.
 * 2. A busca é **debounced** e o campo **não é** — digitar três letras seguidas gera UMA consulta,
 *    enquanto o texto na tela acompanha a tecla.
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

    /** Registra os filtros com que foi observado, na ordem em que chegaram. */
    private inner class FakeRepo(
        private val refreshResult: AppResult<Unit> = AppResult.Success(Unit),
    ) : ExerciseRepository {
        val chamadas = mutableListOf<FiltroDeExercicios>()

        override fun observeExercises(filtro: FiltroDeExercicios): Flow<List<Exercise>> {
            chamadas += filtro
            val lista = if (filtro.musculo != null) emptyList()
            else listOf(exercise("ex-1")).filter { filtro.busca.isBlank() || filtro.busca in it.id }
            return flowOf(lista)
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

        assertEquals(listOf(FiltroDeExercicios()), repo.chamadas)
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

        assertEquals(
            FiltroDeExercicios(categoria = ExerciseCategory.LEGS, musculo = MuscleGroup.GLUTES),
            repo.chamadas.last(),
        )
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

        assertEquals(
            FiltroDeExercicios(categoria = ExerciseCategory.FUNCTIONAL_HIT, musculo = MuscleGroup.CORE),
            repo.chamadas.last(),
        )
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

    @Test
    fun `consumir o erro de sync limpa o aviso e preserva a lista e o filtro`() = runTest(dispatcher) {
        val repo = FakeRepo(refreshResult = AppResult.Failure(AppError.Connection()))
        val vm = ExerciseListViewModel(repo)
        advanceUntilIdle()
        vm.onEvent(ExerciseListEvent.CategorySelected(ExerciseCategory.CROSSFIT))
        advanceUntilIdle()
        assertIs<AppError.Connection>(vm.state.value.error)

        vm.consumeError()

        assertNull(vm.state.value.error)
        assertEquals(ExerciseCategory.CROSSFIT, vm.state.value.selectedCategory)
        assertEquals(listOf("ex-1"), vm.state.value.exercises.map { it.id })
    }

    @Test
    fun `digitar tres letras seguidas gera UMA consulta, com o texto final`() = runTest(dispatcher) {
        val repo = FakeRepo()
        val vm = ExerciseListViewModel(repo)
        advanceUntilIdle()
        val antes = repo.chamadas.size

        vm.onEvent(ExerciseListEvent.BuscaAlterada("e"))
        vm.onEvent(ExerciseListEvent.BuscaAlterada("ex"))
        vm.onEvent(ExerciseListEvent.BuscaAlterada("ex-"))
        advanceUntilIdle()

        assertEquals(
            1, repo.chamadas.size - antes,
            "o debounce não colapsou as teclas -- cada letra relê 923 linhas do SQLite",
        )
        assertEquals("ex-", repo.chamadas.last().busca)
    }

    @Test
    fun `o campo acompanha a tecla sem esperar o debounce`() = runTest(dispatcher) {
        val repo = FakeRepo()
        val vm = ExerciseListViewModel(repo)
        advanceUntilIdle()
        val antes = repo.chamadas.size

        vm.onEvent(ExerciseListEvent.BuscaAlterada("ros"))
        // De propósito SEM avançar o tempo: é o instante entre a tecla e a consulta.

        assertEquals("ros", vm.state.value.busca, "a letra digitada demorou a aparecer no campo")
        assertEquals(antes, repo.chamadas.size, "a consulta não esperou o debounce")
    }

    @Test
    fun `apagar a busca volta a mostrar o acervo`() = runTest(dispatcher) {
        val repo = FakeRepo()
        val vm = ExerciseListViewModel(repo)
        advanceUntilIdle()

        vm.onEvent(ExerciseListEvent.BuscaAlterada("supino"))
        advanceUntilIdle()
        assertEquals(emptyList(), vm.state.value.exercises, "termo sem resultado tinha de esvaziar")

        vm.onEvent(ExerciseListEvent.BuscaAlterada(""))
        advanceUntilIdle()

        assertEquals(listOf("ex-1"), vm.state.value.exercises.map { it.id })
        assertEquals("", repo.chamadas.last().busca)
    }

    @Test
    fun `buscar nao apaga os chips ja escolhidos`() = runTest(dispatcher) {
        val repo = FakeRepo()
        val vm = ExerciseListViewModel(repo)
        advanceUntilIdle()

        vm.onEvent(ExerciseListEvent.CategorySelected(ExerciseCategory.CROSSFIT))
        advanceUntilIdle()
        vm.onEvent(ExerciseListEvent.BuscaAlterada("ex"))
        advanceUntilIdle()

        assertEquals(
            FiltroDeExercicios(busca = "ex", categoria = ExerciseCategory.CROSSFIT),
            repo.chamadas.last(),
        )
        assertEquals(ExerciseCategory.CROSSFIT, vm.state.value.selectedCategory)
    }
}
