package dev.rafael.features.workout.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.rafael.core.catalog.ExerciseLookup
import dev.rafael.contract.error.ErrorCodes
import dev.rafael.core.result.AppError
import dev.rafael.core.result.AppResult
import dev.rafael.features.workout.domain.model.Workout
import dev.rafael.features.workout.domain.repository.WorkoutRepository
import dev.rafael.features.workout.presentation.state.ResolvedExercise
import dev.rafael.features.workout.presentation.state.WorkoutDetailEvent
import dev.rafael.features.workout.presentation.state.WorkoutDetailState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class WorkoutDetailViewModel(
    private val workoutId: String,
    private val repository: WorkoutRepository,
    private val lookup: ExerciseLookup,
) : ViewModel() {

    private val _state = MutableStateFlow(WorkoutDetailState())
    val state: StateFlow<WorkoutDetailState> = _state.asStateFlow()

    // Workout carregado (com ids/séries/rir) pra editar e re-enviar no PUT.
    private var loaded: Workout? = null

    // Sem init { load() }: a tela dispara Retry no ON_RESUME (1ª entrada + refresh ao voltar).
    // Ter os dois causava GET duplicado ao abrir o detalhe do treino.

    fun onEvent(event: WorkoutDetailEvent) {
        when (event) {
            WorkoutDetailEvent.Retry -> load()
            WorkoutDetailEvent.Delete -> delete()
            is WorkoutDetailEvent.SwapExercise -> swap(event.orderIndex, event.newExerciseId)
            is WorkoutDetailEvent.RemoveExercise -> removeExercise(event.orderIndex)
            WorkoutDetailEvent.ShowPaywall -> _state.update { it.copy(showPaywall = true) }
            WorkoutDetailEvent.DismissPaywall -> _state.update { it.copy(showPaywall = false) }
        }
    }

    private fun load() {
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            when (val result = repository.get(workoutId)) {
                is AppResult.Success -> applyWorkout(result.value)
                is AppResult.Failure ->
                    _state.update { it.copy(isLoading = false, error = result.error) }
            }
        }
    }

    /** Guarda o workout e re-resolve os nomes pra exibição. */
    private suspend fun applyWorkout(workout: Workout) {
        loaded = workout
        val refs = lookup.byIds(workout.exercises.map { it.exerciseId })
        _state.update {
            it.copy(
                isLoading = false,
                id = workout.id,
                name = workout.name,
                exercises = workout.toResolved(refs),
            )
        }
    }

    private fun swap(orderIndex: Int, newExerciseId: String) {
        val w = loaded ?: return
        val novo = w.copy(
            exercises = w.exercises.map {
                if (it.orderIndex == orderIndex) it.copy(exerciseId = newExerciseId) else it
            },
        )
        save(novo)
    }

    private fun removeExercise(orderIndex: Int) {
        val w = loaded ?: return
        if (w.exercises.size <= 1) {
            _state.update { it.copy(error = AppError.Validation("O treino precisa de ao menos 1 exercício.")) }
            return
        }
        val restantes = w.exercises
            .filter { it.orderIndex != orderIndex }
            .sortedBy { it.orderIndex }
            .mapIndexed { i, ex -> ex.copy(orderIndex = i) }   // renumera pra não deixar buraco
        save(w.copy(exercises = restantes))
    }

    /** PUT do workout editado. 403 ENTITLEMENT (editar programa IA) → paywall. */
    private fun save(newWorkout: Workout) {
        val id = newWorkout.id ?: return
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            when (val result = repository.update(id, newWorkout)) {
                is AppResult.Success -> {
                    applyWorkout(result.value)
                    // mutação aplicada: a agenda/contagem do programa mudou → a tela avisa
                    // o app na volta, e só então o cache de programas é invalidado.
                    _state.update { it.copy(alterado = true) }
                }
                is AppResult.Failure -> {
                    val err = result.error
                    if (err is AppError.Forbidden && err.code == ErrorCodes.ENTITLEMENT_REQUIRED)
                        _state.update { it.copy(isLoading = false, showPaywall = true) }
                    else
                        _state.update { it.copy(isLoading = false, error = err) }
                }
            }
        }
    }

    private fun delete() {
        viewModelScope.launch {
            when (val result = repository.delete(workoutId)) {
                is AppResult.Success -> _state.update { it.copy(isDeleted = true) }
                is AppResult.Failure -> _state.update { it.copy(error = result.error) }
            }
        }
    }
}

private fun Workout.toResolved(refs: Map<String, dev.rafael.core.catalog.ExerciseRef>) =
    exercises
        .sortedBy { it.orderIndex }
        .map { ex ->
            val ref = refs[ex.exerciseId]
            val reps = ex.sets.sortedBy { it.orderIndex }.joinToString("/") { it.reps.toString() }
            ResolvedExercise(
                exerciseId = ex.exerciseId,
                name = ref?.name ?: "Exercício indisponível",   // degrada, não crasha
                thumbRef = ref?.thumbRef,
                setsSummary = "${ex.sets.size} séries · $reps reps",
                orderIndex = ex.orderIndex,
            )
        }
