package dev.rafael.app.screens.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.rafael.app.data.session.SessionSync
import dev.rafael.contract.session.SetLogDto
import dev.rafael.contract.session.WorkoutSessionDto
import dev.rafael.core.catalog.ExerciseLookup
import dev.rafael.core.result.AppResult
import dev.rafael.features.workout.domain.repository.WorkoutRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.uuid.Uuid

/** Uma série editável na execução (reps/carga como texto p/ o input). */
data class SetEntry(
    val exerciseId: String,
    val exerciseName: String,
    val orderIndex: Int,
    val setIndex: Int,
    val targetReps: Int,
    val repsDone: String,
    val weight: String,
    val done: Boolean,
)

data class WorkoutSessionState(
    val workoutName: String = "",
    val entries: List<SetEntry> = emptyList(),
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val error: String? = null,
    val saved: Boolean = false,
) {
    val canFinish: Boolean get() = !isSaving && entries.any { it.done }
}

sealed interface SessionEvent {
    data class RepsChanged(val index: Int, val value: String) : SessionEvent
    data class WeightChanged(val index: Int, val value: String) : SessionEvent
    data class ToggleDone(val index: Int) : SessionEvent
    data object Finish : SessionEvent
}

class WorkoutSessionViewModel(
    private val workoutId: String,
    private val workouts: WorkoutRepository,
    private val lookup: ExerciseLookup,
    private val sync: SessionSync,
) : ViewModel() {

    private val _state = MutableStateFlow(WorkoutSessionState())
    val state: StateFlow<WorkoutSessionState> = _state.asStateFlow()

    private val startedAt = nowIso()   // início = abertura da tela
    private var programId: String? = null

    init { load() }

    fun onEvent(event: SessionEvent) {
        when (event) {
            is SessionEvent.RepsChanged -> updateEntry(event.index) { it.copy(repsDone = event.value.filter { c -> c.isDigit() }) }
            is SessionEvent.WeightChanged -> updateEntry(event.index) { it.copy(weight = event.value.filter { c -> c.isDigit() || c == '.' }) }
            is SessionEvent.ToggleDone -> updateEntry(event.index) { it.copy(done = !it.done) }
            SessionEvent.Finish -> finish()
        }
    }

    private fun load() {
        viewModelScope.launch {
            when (val r = workouts.get(workoutId)) {
                is AppResult.Success -> {
                    val w = r.value
                    programId = w.programId
                    val refs = lookup.byIds(w.exercises.map { it.exerciseId })
                    val entries = w.exercises.sortedBy { it.orderIndex }.flatMap { ex ->
                        ex.sets.sortedBy { it.orderIndex }.map { set ->
                            SetEntry(
                                exerciseId = ex.exerciseId,
                                exerciseName = refs[ex.exerciseId]?.name ?: "Exercício",
                                orderIndex = ex.orderIndex,
                                setIndex = set.orderIndex,
                                targetReps = set.reps,
                                repsDone = set.reps.toString(),   // começa no alvo
                                weight = "",
                                done = false,
                            )
                        }
                    }
                    _state.update { it.copy(isLoading = false, workoutName = w.name, entries = entries) }
                }
                is AppResult.Failure -> _state.update { it.copy(isLoading = false, error = r.error.message) }
            }
        }
    }

    private fun finish() {
        val s = _state.value
        if (!s.canFinish) return
        _state.update { it.copy(isSaving = true, error = null) }
        val dto = WorkoutSessionDto(
            id = Uuid.random().toString(),           // idempotência do sync
            programId = programId,
            workoutId = workoutId,
            workoutName = s.workoutName,
            startedAt = startedAt,
            finishedAt = nowIso(),
            sets = s.entries.map { e ->
                SetLogDto(
                    exerciseId = e.exerciseId,
                    orderIndex = e.orderIndex,
                    setIndex = e.setIndex,
                    targetReps = e.targetReps,
                    repsDone = e.repsDone.toIntOrNull() ?: 0,
                    weightKg = e.weight.toDoubleOrNull(),
                    done = e.done,
                )
            },
        )
        viewModelScope.launch {
            runCatching { sync.record(dto) }.fold(
                onSuccess = { _state.update { it.copy(isSaving = false, saved = true) } },   // salvo (local ao menos)
                onFailure = { _state.update { it.copy(isSaving = false, error = "Não deu pra salvar o treino.") } },
            )
        }
    }

    private fun updateEntry(index: Int, transform: (SetEntry) -> SetEntry) {
        _state.update { st ->
            st.copy(entries = st.entries.mapIndexed { i, e -> if (i == index) transform(e) else e })
        }
    }

    private fun nowIso() = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).toString()
}
