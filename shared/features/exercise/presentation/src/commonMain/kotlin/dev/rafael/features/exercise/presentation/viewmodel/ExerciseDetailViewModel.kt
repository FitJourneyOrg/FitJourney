package dev.rafael.features.exercise.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.rafael.core.result.AppError
import dev.rafael.core.result.AppResult
import dev.rafael.features.exercise.domain.repository.ExerciseRepository
import dev.rafael.features.exercise.presentation.state.ExerciseDetailState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Busca o detalhe completo (com taxonomia) da REDE — o cache local não guarda a taxonomia, e o
 * detalhe já exige rede pra mídia. Só init (a tela não tem ON_RESUME) → sem GET duplicado.
 */
class ExerciseDetailViewModel(
    private val exerciseId: String,
    private val repository: ExerciseRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ExerciseDetailState())
    val state: StateFlow<ExerciseDetailState> = _state.asStateFlow()

    init { load() }

    fun retry() = load()

    private fun load() {
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            when (val r = repository.getDetail(exerciseId)) {
                is AppResult.Success ->
                    _state.update { it.copy(isLoading = false, exercise = r.value) }
                is AppResult.Failure ->
                    _state.update { it.copy(isLoading = false, error = r.error) }
            }
        }
    }
}
