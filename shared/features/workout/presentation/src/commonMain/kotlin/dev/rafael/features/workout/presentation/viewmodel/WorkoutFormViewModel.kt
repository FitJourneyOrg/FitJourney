package dev.rafael.features.workout.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.rafael.core.catalog.ExerciseLookup
import dev.rafael.core.result.AppResult
import dev.rafael.features.workout.domain.model.Workout
import dev.rafael.features.workout.domain.model.WorkoutExercise
import dev.rafael.features.workout.domain.model.WorkoutSet
import dev.rafael.features.workout.domain.repository.WorkoutRepository
import dev.rafael.features.workout.presentation.state.FormExercise
import dev.rafael.features.workout.presentation.state.WorkoutFormEvent
import dev.rafael.features.workout.presentation.state.WorkoutFormState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val DEFAULT_REPS = "12"

class WorkoutFormViewModel(
    private val workoutId: String?,          // null = criar
    private val programId: String?,          // obrigatório se workoutId == null (ARCH #26)
    private val repository: WorkoutRepository,
    private val lookup: ExerciseLookup,
) : ViewModel() {

    private val _state = MutableStateFlow(WorkoutFormState(workoutId = workoutId, programId = programId))
    val state: StateFlow<WorkoutFormState> = _state.asStateFlow()

    init { if (workoutId != null) loadExisting(workoutId) }

    fun onEvent(event: WorkoutFormEvent) {
        when (event) {
            is WorkoutFormEvent.NameChanged ->
                _state.update { it.copy(name = event.value) }

            is WorkoutFormEvent.ExercisesAdded -> addExercises(event.ids)

            is WorkoutFormEvent.ExerciseRemoved ->
                _state.update { it.copy(exercises = it.exercises.removeAt(event.index)) }

            is WorkoutFormEvent.ExerciseMovedUp -> move(event.index, event.index - 1)
            is WorkoutFormEvent.ExerciseMovedDown -> move(event.index, event.index + 1)

            is WorkoutFormEvent.SetAdded ->
                updateExercise(event.exerciseIndex) { it.copy(sets = it.sets + DEFAULT_REPS) }

            is WorkoutFormEvent.SetRemoved ->
                updateExercise(event.exerciseIndex) { ex ->
                    if (ex.sets.size <= 1) ex else ex.copy(sets = ex.sets.removeAt(event.setIndex))
                }

            is WorkoutFormEvent.SetRepsChanged ->
                updateExercise(event.exerciseIndex) { ex ->
                    ex.copy(sets = ex.sets.replaceAt(event.setIndex, event.reps.filter { it.isDigit() }))
                }

            WorkoutFormEvent.Save -> save()
            WorkoutFormEvent.Retry -> workoutId?.let { loadExisting(it) }
        }
    }

    private fun loadExisting(id: String) {
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            when (val result = repository.get(id)) {
                is AppResult.Success -> {
                    val w = result.value
                    val refs = lookup.byIds(w.exercises.map { it.exerciseId })
                    _state.update {
                        it.copy(
                            isLoading = false,
                            name = w.name,
                            exercises = w.exercises.sortedBy { e -> e.orderIndex }.map { e ->
                                FormExercise(
                                    exerciseId = e.exerciseId,
                                    name = refs[e.exerciseId]?.name ?: "Exercício indisponível",
                                    sets = e.sets.sortedBy { s -> s.orderIndex }.map { s -> s.reps.toString() },
                                )
                            },
                        )
                    }
                }
                is AppResult.Failure ->
                    _state.update { it.copy(isLoading = false, error = result.error.message) }
            }
        }
    }

    private fun addExercises(ids: List<String>) {
        if (ids.isEmpty()) return
        viewModelScope.launch {
            val refs = lookup.byIds(ids)
            val novos = ids.map { id ->
                FormExercise(
                    exerciseId = id,
                    name = refs[id]?.name ?: "Exercício indisponível",
                    sets = listOf(DEFAULT_REPS),      // nasce com 1 série (server exige ≥1)
                )
            }
            _state.update { it.copy(exercises = it.exercises + novos) }
        }
    }

    private fun save() {
        val s = _state.value
        if (!s.canSave) return
        _state.update { it.copy(isSaving = true, error = null) }

        val workout = Workout(
            id = s.workoutId,
            name = s.name.trim(),
            programId = s.programId,
            exercises = s.exercises.mapIndexed { i, ex ->
                WorkoutExercise(
                    exerciseId = ex.exerciseId,
                    orderIndex = i,                                    // derivado da posição
                    sets = ex.sets.mapIndexed { j, reps ->
                        WorkoutSet(reps = reps.toInt(), orderIndex = j)
                    },
                )
            },
            createdAt = null,
            updatedAt = null,
        )

        viewModelScope.launch {
            val result = if (s.workoutId == null) repository.create(workout)
            else repository.update(s.workoutId, workout)
            when (result) {
                is AppResult.Success ->
                    _state.update { it.copy(isSaving = false, savedId = result.value.id) }
                is AppResult.Failure ->
                    _state.update { it.copy(isSaving = false, error = result.error.message) }
            }
        }
    }

    private fun move(from: Int, to: Int) {
        val list = _state.value.exercises
        if (to !in list.indices) return
        _state.update { it.copy(exercises = list.toMutableList().apply { add(to, removeAt(from)) }) }
    }

    private fun updateExercise(index: Int, transform: (FormExercise) -> FormExercise) {
        _state.update { st ->
            st.copy(exercises = st.exercises.mapIndexed { i, ex -> if (i == index) transform(ex) else ex })
        }
    }
}

// helpers imutáveis
private fun <T> List<T>.removeAt(index: Int): List<T> = filterIndexed { i, _ -> i != index }
private fun <T> List<T>.replaceAt(index: Int, value: T): List<T> = mapIndexed { i, v -> if (i == index) value else v }