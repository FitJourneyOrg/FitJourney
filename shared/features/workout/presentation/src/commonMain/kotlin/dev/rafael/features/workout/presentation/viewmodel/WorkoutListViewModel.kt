package dev.rafael.features.workout.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.rafael.core.result.AppResult
import dev.rafael.features.workout.domain.repository.WorkoutRepository
import dev.rafael.features.workout.presentation.state.WorkoutListEvent
import dev.rafael.features.workout.presentation.state.WorkoutListState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class WorkoutListViewModel(
    private val repository: WorkoutRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(WorkoutListState())
    val state: StateFlow<WorkoutListState> = _state.asStateFlow()

    init { load() }

    fun onEvent(event: WorkoutListEvent) {
        when (event) {
            WorkoutListEvent.Load, WorkoutListEvent.Retry -> load()
        }
    }

    private fun load() {
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            when (val result = repository.list()) {
                is AppResult.Success ->
                    _state.update { it.copy(isLoading = false, workouts = result.value) }
                is AppResult.Failure ->
                    _state.update { it.copy(isLoading = false, error = result.error.message) }
            }
        }
    }
}