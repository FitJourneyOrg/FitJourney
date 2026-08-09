package dev.rafael.features.exercise.presentation.viewmodel

import dev.rafael.contract.exercise.ExerciseCategory
import dev.rafael.contract.profile.Level
import dev.rafael.contract.profile.MuscleGroup
import dev.rafael.core.result.AppError
import dev.rafael.core.result.AppResult
import dev.rafael.features.exercise.domain.model.Exercise
import dev.rafael.features.exercise.domain.repository.ExerciseRepository
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
import kotlin.test.assertFalse
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class ExerciseDetailViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest fun setup() { Dispatchers.setMain(dispatcher) }
    @AfterTest fun tearDown() { Dispatchers.resetMain() }

    private fun exercise(id: String = "ex-1") = Exercise(
        id = id, name = "Supino", category = ExerciseCategory.CHEST,
        description = "desc", videoRef = "v.mp4", thumbRef = "t.png",
        primaryMuscles = listOf(MuscleGroup.CHEST), secondaryMuscles = emptyList(),
        equipment = "BARBELL", movementPattern = null,
        isCompound = true, unilateral = false, prescriptionType = "REPS", level = Level.INTERMEDIATE,
    )

    private class FakeRepo(private val detail: AppResult<Exercise>) : ExerciseRepository {
        override fun observeExercises(category: ExerciseCategory?): Flow<List<Exercise>> = flowOf(emptyList())
        override suspend fun refresh(): AppResult<Unit> = AppResult.Success(Unit)
        override suspend fun alternatives(exerciseId: String): AppResult<List<Exercise>> = AppResult.Success(emptyList())
        override suspend fun getDetail(exerciseId: String): AppResult<Exercise> = detail
    }

    @Test
    fun `carrega o exercicio (com taxonomia) pelo id`() = runTest(dispatcher) {
        val vm = ExerciseDetailViewModel("ex-1", FakeRepo(AppResult.Success(exercise("ex-1"))))
        advanceUntilIdle()

        val s = vm.state.value
        assertFalse(s.isLoading)
        assertEquals("ex-1", s.exercise?.id)
        assertEquals("BARBELL", s.exercise?.equipment)
        assertEquals(listOf(MuscleGroup.CHEST), s.exercise?.primaryMuscles)
    }

    @Test
    fun `falha seta erro e mantem exercicio nulo`() = runTest(dispatcher) {
        val vm = ExerciseDetailViewModel("ex-1", FakeRepo(AppResult.Failure(AppError.NotFound())))
        advanceUntilIdle()

        assertNull(vm.state.value.exercise)
        assertEquals("Não encontrado", vm.state.value.error)
    }
}
