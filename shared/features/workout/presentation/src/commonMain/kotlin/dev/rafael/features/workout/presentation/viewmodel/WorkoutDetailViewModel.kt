package dev.rafael.features.workout.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.rafael.core.catalog.ExerciseLookup
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

    init { load() }

    fun onEvent(event: WorkoutDetailEvent) {
        when (event) {
            WorkoutDetailEvent.Retry -> load()
            WorkoutDetailEvent.Delete -> delete()
        }
    }

    private fun load() {
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            when (val result = repository.get(workoutId)) {
                is AppResult.Success -> {
                    val workout = result.value
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
                is AppResult.Failure ->
                    _state.update { it.copy(isLoading = false, error = result.error.message) }
            }
        }
    }

    private fun delete() {
        viewModelScope.launch {
            when (val result = repository.delete(workoutId)) {
                is AppResult.Success -> _state.update { it.copy(isDeleted = true) }
                is AppResult.Failure -> _state.update { it.copy(error = result.error.message) }
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