package dev.rafael.app.screens.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.rafael.app.data.session.HistoricoDeSessoes
import dev.rafael.contract.session.SetLogDto
import dev.rafael.contract.session.WorkoutSessionDto
import dev.rafael.core.catalog.ExerciseLookup
import dev.rafael.core.result.AppError
import dev.rafael.core.result.AppResult
import dev.rafael.features.workout.domain.repository.WorkoutRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
    val restSeconds: Int,   // prescrito pelo motor (ARCH #26 §3.2)
)

data class WorkoutSessionState(
    val workoutName: String = "",
    val entries: List<SetEntry> = emptyList(),
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val error: AppError? = null,
    val saved: Boolean = false,
    // --- descanso ---
    val restRemaining: Int? = null,   // segundos restantes; null = sem descanso rodando
    val restTotal: Int = 0,           // duração prescrita (p/ a barra de progresso)
    val restDoneTick: Int = 0,        // incrementa quando um descanso zera (UI vibra)
) {
    val canFinish: Boolean get() = !isSaving && entries.any { it.done }
}

sealed interface SessionEvent {
    data class RepsChanged(val index: Int, val value: String) : SessionEvent
    data class WeightChanged(val index: Int, val value: String) : SessionEvent
    data class ToggleDone(val index: Int) : SessionEvent
    data object Finish : SessionEvent
    // descanso
    data object SkipRest : SessionEvent
    data class AddRest(val seconds: Int) : SessionEvent
}

class WorkoutSessionViewModel(
    private val workoutId: String,
    private val workouts: WorkoutRepository,
    private val lookup: ExerciseLookup,
    private val sync: HistoricoDeSessoes,
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
            is SessionEvent.ToggleDone -> {
                val marcando = _state.value.entries.getOrNull(event.index)?.done == false
                updateEntry(event.index) { it.copy(done = !it.done) }
                // só ao MARCAR (desmarcar é correção, não gera descanso)
                if (marcando) _state.value.entries.getOrNull(event.index)?.let { startRest(it.restSeconds) }
            }
            SessionEvent.Finish -> finish()
            SessionEvent.SkipRest -> stopRest()
            is SessionEvent.AddRest -> addRest(event.seconds)
        }
    }

    // ---- descanso ----------------------------------------------------------
    // Guarda o INSTANTE DO FIM e recalcula o restante a cada tick. Um contador puro
    // atrasaria se o app fosse pro fundo; assim o tempo continua correto ao voltar.

    private var restEndsAt: Long? = null
    private var restJob: Job? = null

    private fun startRest(seconds: Int) {
        if (seconds <= 0) return
        restEndsAt = nowMs() + seconds * 1000L
        _state.update { it.copy(restRemaining = seconds, restTotal = seconds) }
        restJob?.cancel()
        restJob = viewModelScope.launch {
            while (true) {
                delay(250)
                val fim = restEndsAt ?: break
                val restante = ((fim - nowMs() + 999) / 1000).toInt()   // arredonda p/ cima
                if (restante <= 0) {
                    restEndsAt = null
                    _state.update { it.copy(restRemaining = null, restDoneTick = it.restDoneTick + 1) }
                    break
                }
                if (restante != _state.value.restRemaining) {
                    _state.update { it.copy(restRemaining = restante) }
                }
            }
        }
    }

    private fun addRest(seconds: Int) {
        val fim = restEndsAt ?: return
        restEndsAt = fim + seconds * 1000L
        _state.update { it.copy(restTotal = it.restTotal + seconds) }
    }

    private fun stopRest() {
        restJob?.cancel()
        restEndsAt = null
        _state.update { it.copy(restRemaining = null) }
    }

    override fun onCleared() {
        restJob?.cancel()
        super.onCleared()
    }

    private fun nowMs() = Clock.System.now().toEpochMilliseconds()

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
                                restSeconds = ex.restSeconds,     // prescrição do motor
                            )
                        }
                    }
                    _state.update { it.copy(isLoading = false, workoutName = w.name, entries = entries) }
                }
                is AppResult.Failure -> _state.update { it.copy(isLoading = false, error = r.error) }
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
                onFailure = { _state.update { it.copy(isSaving = false, error = AppError.Unexpected("Não deu pra salvar o treino.")) } },
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
