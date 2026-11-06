package dev.rafael.features.session.presentation.viewmodel

import dev.rafael.contract.session.SetLogDto
import dev.rafael.contract.session.WorkoutSessionDto
import dev.rafael.core.catalog.ExerciseLookup
import dev.rafael.core.catalog.ExerciseRef
import dev.rafael.core.result.AppResult
import dev.rafael.features.session.domain.HistoricoDeSessoes
import dev.rafael.features.session.domain.SessaoLocal
import dev.rafael.features.session.presentation.state.SessionEvent
import dev.rafael.features.workout.domain.model.Workout
import dev.rafael.features.workout.domain.model.WorkoutExercise
import dev.rafael.features.workout.domain.model.WorkoutSet
import dev.rafael.features.workout.domain.repository.WorkoutRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * `SetEntry.rir` → `SessionEvent.RirChanged` → `finish()` → `SetLogDto.rir`, fatia
 * "rir-chega-e-registra" (2026-11-05), etapa Cliente.
 *
 * ⚠️ Não usa `advanceUntilIdle()`: o `init` dispara `contarOTempoDaSessao()`, um laço
 * `while (true) { ...; delay(1_000) }` que nunca termina. `advanceUntilIdle()` persegue a fila do
 * scheduler até ela esvaziar, e esse laço reagenda a si mesmo pra sempre — o teste travaria em
 * tempo virtual infinito. `runCurrent()` resolve só o que já está pronto (sem avançar o relógio).
 *
 * E mesmo com `runCurrent()`, o laço continua VIVO (só suspenso no primeiro `delay`) quando o
 * teste retorna -- fora do `ViewModelStore` do Android, nada chama `clear()`/`onCleared()`
 * sozinho. Por isso todo teste termina chamando `vm.onCleared()`: sem isso, a corrotina do
 * cronômetro (e a do descanso, se o teste tocou nela) fica pendurada no dispatcher e o PROCESSO
 * de teste trava esperando algo que nunca termina -- exatamente o que aconteceu na primeira
 * versão deste arquivo (`onCleared()` era `protected`, o teste não tinha como chamar).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WorkoutSessionViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest fun setup() { Dispatchers.setMain(dispatcher) }
    @AfterTest fun tearDown() { Dispatchers.resetMain() }

    private fun workoutDeUmaSerie() = Workout(
        id = "workout-1",
        name = "Treino A",
        programId = "programa-1",
        exercises = listOf(
            WorkoutExercise(
                exerciseId = "ex-1",
                orderIndex = 0,
                restSeconds = 90,
                sets = listOf(WorkoutSet(reps = 10, orderIndex = 0)),
            ),
        ),
        createdAt = null,
        updatedAt = null,
    )

    private class FakeWorkoutRepository(private val workout: Workout) : WorkoutRepository {
        override suspend fun list(): AppResult<List<dev.rafael.features.workout.domain.model.WorkoutSummary>> =
            error("não usado neste teste")
        override suspend fun get(id: String): AppResult<Workout> = AppResult.Success(workout)
        override suspend fun create(workout: Workout): AppResult<Workout> = error("não usado neste teste")
        override suspend fun update(id: String, workout: Workout): AppResult<Workout> = error("não usado neste teste")
        override suspend fun delete(id: String): AppResult<Unit> = error("não usado neste teste")
    }

    private class FakeExerciseLookup : ExerciseLookup {
        override suspend fun byIds(ids: List<String>): Map<String, ExerciseRef> =
            ids.associateWith { ExerciseRef(id = it, name = "Supino", thumbRef = "t.jpg", videoRef = "v.mp4") }
    }

    /** Captura o DTO gravado — é nele que a asserção de `rir` olha. */
    private class FakeHistoricoDeSessoes : HistoricoDeSessoes {
        var gravado: WorkoutSessionDto? = null
        override fun observarHistorico(): Flow<List<SessaoLocal>> = emptyFlow()
        override suspend fun record(dto: WorkoutSessionDto) { gravado = dto }
        override suspend fun flush() {}
        override suspend fun sincronizarHistorico(forcar: Boolean): AppResult<Unit> = AppResult.Success(Unit)
    }

    private fun criarViewModel(sync: FakeHistoricoDeSessoes) = WorkoutSessionViewModel(
        workoutId = "workout-1",
        workouts = FakeWorkoutRepository(workoutDeUmaSerie()),
        lookup = FakeExerciseLookup(),
        sync = sync,
    )

    @Test
    fun `RirChanged aceita so digitos e finish carrega o valor no SetLogDto`() = runTest(dispatcher) {
        val sync = FakeHistoricoDeSessoes()
        val vm = criarViewModel(sync)
        dispatcher.scheduler.runCurrent()   // termina o load() (ver nota da classe sobre o timer)

        vm.onEvent(SessionEvent.RirChanged(0, "2"))
        vm.onEvent(SessionEvent.ToggleDone(0))   // canFinish exige ao menos uma série feita
        vm.onEvent(SessionEvent.Finish)
        dispatcher.scheduler.runCurrent()

        val dto = sync.gravado
        requireNotNull(dto) { "finish() não gravou nada" }
        assertEquals(2, dto.sets.single().rir)
        vm.onCleared()
    }

    @Test
    fun `rir em branco chega como null no DTO -- nao informado, nao zero`() = runTest(dispatcher) {
        // Caminho de falha: D-3 exige que "não respondeu" e "respondeu 0" sejam coisas
        // diferentes. Se `finish()` trocasse `""` por `0`, a progressão trataria quem não
        // informou RIR como quem treinou até a falha -- o oposto do que D-3 garante.
        val sync = FakeHistoricoDeSessoes()
        val vm = criarViewModel(sync)
        dispatcher.scheduler.runCurrent()

        vm.onEvent(SessionEvent.ToggleDone(0))   // marca feita sem nunca tocar no RIR
        vm.onEvent(SessionEvent.Finish)
        dispatcher.scheduler.runCurrent()

        val dto = sync.gravado
        requireNotNull(dto) { "finish() não gravou nada" }
        assertNull(dto.sets.single().rir)
        vm.onCleared()
    }

    @Test
    fun `RirChanged filtra caracteres nao numericos`() {
        // Tier 1 (sem corrotina): mesmo filtro de RepsChanged/WeightChanged, aplicado a rir.
        val vm = criarViewModel(FakeHistoricoDeSessoes())
        dispatcher.scheduler.runCurrent()

        vm.onEvent(SessionEvent.RirChanged(0, "ab3c"))

        assertEquals("3", vm.state.value.entries.single().rir)
        vm.onCleared()
    }
}
