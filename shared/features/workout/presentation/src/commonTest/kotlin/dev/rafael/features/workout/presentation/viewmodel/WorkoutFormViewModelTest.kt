package dev.rafael.features.workout.presentation.viewmodel

import dev.rafael.core.catalog.ExerciseLookup
import dev.rafael.core.catalog.ExerciseRef
import dev.rafael.core.result.AppResult
import dev.rafael.features.workout.domain.model.Workout
import dev.rafael.features.workout.domain.model.WorkoutExercise
import dev.rafael.features.workout.domain.model.WorkoutSet
import dev.rafael.features.workout.domain.model.WorkoutSummary
import dev.rafael.features.workout.domain.repository.WorkoutRepository
import dev.rafael.features.workout.presentation.state.WorkoutFormEvent
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
import kotlin.test.assertNotNull

/**
 * Prova o núcleo da edição premium (item 1): editar um treino de IA pelo form NÃO pode
 * zerar rir/restSeconds (prescrição do motor #26). O form não expõe esses campos, então
 * eles têm que sobreviver ao round-trip load → save.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WorkoutFormViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest fun setup() { Dispatchers.setMain(dispatcher) }
    @AfterTest fun tearDown() { Dispatchers.resetMain() }

    private class FakeRepo(private val existing: Workout) : WorkoutRepository {
        var lastUpdated: Workout? = null
        override suspend fun list() = AppResult.Success(emptyList<WorkoutSummary>())
        override suspend fun get(id: String) = AppResult.Success(existing)
        override suspend fun create(workout: Workout) = AppResult.Success(workout)
        override suspend fun update(id: String, workout: Workout): AppResult<Workout> {
            lastUpdated = workout
            return AppResult.Success(workout)
        }
        override suspend fun delete(id: String) = AppResult.Success(Unit)
    }

    private class FakeLookup : ExerciseLookup {
        override suspend fun byIds(ids: List<String>) =
            ids.associateWith { ExerciseRef(it, "Exercício $it", null) }
    }

    private fun aiWorkout() = Workout(
        id = "w1", name = "Upper A", programId = "prog-1",
        exercises = listOf(
            WorkoutExercise(
                exerciseId = "ex-0", orderIndex = 0, restSeconds = 120, rir = 2,
                sets = listOf(WorkoutSet(reps = 8, orderIndex = 0), WorkoutSet(reps = 8, orderIndex = 1)),
            ),
            WorkoutExercise(
                exerciseId = "ex-1", orderIndex = 1, restSeconds = 90, rir = 1,
                sets = listOf(WorkoutSet(reps = 12, orderIndex = 0)),
            ),
        ),
        createdAt = null, updatedAt = null,
    )

    @Test
    fun `editar treino de IA preserva rir e restSeconds no save`() = runTest(dispatcher) {
        val repo = FakeRepo(aiWorkout())
        val vm = WorkoutFormViewModel(workoutId = "w1", programId = null, takenDaysCsv = "", repository = repo, lookup = FakeLookup())
        advanceUntilIdle()

        // usuário mexe só nas reps de uma série — rir/restSeconds nem aparecem na UI
        vm.onEvent(WorkoutFormEvent.SetRepsChanged(exerciseIndex = 0, setIndex = 0, reps = "10"))
        vm.onEvent(WorkoutFormEvent.Save)
        advanceUntilIdle()

        val saved = repo.lastUpdated
        assertNotNull(saved, "deveria ter chamado update")
        assertEquals(2, saved.exercises[0].rir, "rir do 1º exercício preservado")
        assertEquals(120, saved.exercises[0].restSeconds, "restSeconds do 1º exercício preservado")
        assertEquals(1, saved.exercises[1].rir)
        assertEquals(90, saved.exercises[1].restSeconds)
        assertEquals(10, saved.exercises[0].sets[0].reps, "a edição de reps foi aplicada")
    }

    @Test
    fun `exercicio adicionado nasce com defaults (rir nulo, rest 90)`() = runTest(dispatcher) {
        val repo = FakeRepo(aiWorkout())
        val vm = WorkoutFormViewModel(workoutId = "w1", programId = null, takenDaysCsv = "", repository = repo, lookup = FakeLookup())
        advanceUntilIdle()

        vm.onEvent(WorkoutFormEvent.ExercisesAdded(listOf("ex-novo")))
        advanceUntilIdle()
        vm.onEvent(WorkoutFormEvent.Save)
        advanceUntilIdle()

        val saved = repo.lastUpdated
        assertNotNull(saved)
        val novo = saved.exercises.first { it.exerciseId == "ex-novo" }
        assertEquals(null, novo.rir, "exercício manual não prescreve RIR")
        assertEquals(90, novo.restSeconds)
        // e os originais seguem preservados
        assertEquals(2, saved.exercises.first { it.exerciseId == "ex-0" }.rir)
    }
}
