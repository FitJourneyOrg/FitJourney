package dev.rafael.features.exercise.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.rafael.contract.exercise.ExerciseCategory
import dev.rafael.core.result.AppResult
import dev.rafael.features.exercise.domain.repository.ExerciseRepository
import dev.rafael.features.exercise.presentation.state.ExerciseListEvent
import dev.rafael.features.exercise.presentation.state.ExerciseListState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ExerciseListViewModel(
    private val repository: ExerciseRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ExerciseListState())
    val state: StateFlow<ExerciseListState> = _state.asStateFlow()

    private var observeJob: Job? = null

    init {
        observe(category = null)   // observa o banco (todas)
        refresh()                  // network-first: popula o cache on-load
    }

    fun onEvent(event: ExerciseListEvent) {
        when (event) {
            is ExerciseListEvent.CategorySelected -> {
                _state.update { it.copy(selectedCategory = event.category) }
                observe(event.category)   // re-observa com o novo filtro
            }
            ExerciseListEvent.Refresh -> refresh()
        }
    }

    /** Cancela a coleta anterior e observa o banco com o filtro atual. */
    private fun observe(category: ExerciseCategory?) {
        observeJob?.cancel()
        observeJob = repository.observeExercises(category)
            .onEach { list -> _state.update { it.copy(exercises = list) } }
            .launchIn(viewModelScope)
    }

    private fun refresh() {
        _state.update { it.copy(isRefreshing = true, error = null) }
        viewModelScope.launch {
            when (val result = repository.refresh()) {
                is AppResult.Success ->
                    _state.update { it.copy(isRefreshing = false) }   // banco atualiza a lista sozinho
                is AppResult.Failure ->
                    _state.update { it.copy(isRefreshing = false, error = result.error.message) }
            }
        }
    }
}