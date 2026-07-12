package dev.rafael.features.workout.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.rafael.core.result.AppError
import dev.rafael.core.result.AppResult
import dev.rafael.features.workout.domain.repository.WorkoutRepository
import dev.rafael.features.workout.presentation.state.GenerateError
import dev.rafael.features.workout.presentation.state.WorkoutGenerateEvent
import dev.rafael.features.workout.presentation.state.WorkoutGenerateState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class WorkoutGenerateViewModel(
    private val repository: WorkoutRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(WorkoutGenerateState())
    val state: StateFlow<WorkoutGenerateState> = _state.asStateFlow()

    fun onEvent(event: WorkoutGenerateEvent) {
        when (event) {
            is WorkoutGenerateEvent.PromptChanged ->
                _state.update { it.copy(prompt = event.value) }
            WorkoutGenerateEvent.Generate -> generate()
            WorkoutGenerateEvent.DismissError ->
                _state.update { it.copy(error = null) }
        }
    }

    private fun generate() {
        _state.update { it.copy(isGenerating = true, error = null) }
        viewModelScope.launch {
            val prompt = _state.value.prompt.ifBlank { null }
            when (val result = repository.generate(prompt)) {
                is AppResult.Success ->
                    _state.update { it.copy(isGenerating = false, generatedId = result.value.id) }
                is AppResult.Failure -> {
                    val err = result.error
                    val ge = when {
                        err is AppError.Forbidden && err.code == "ENTITLEMENT_REQUIRED" ->
                            GenerateError.Entitlement
                        err is AppError.Forbidden && err.code == "HEALTH_GATE_REQUIRED" ->
                            GenerateError.HealthGate
                        else -> GenerateError.Other(err.message)
                    }
                    _state.update { it.copy(isGenerating = false, error = ge) }
                }
            }
        }
    }
}