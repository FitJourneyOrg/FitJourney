package dev.rafael.features.program.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.rafael.core.result.AppError
import dev.rafael.core.result.AppResult
import dev.rafael.features.program.domain.repository.ProgramRepository
import dev.rafael.features.program.presentation.state.GenerateError
import dev.rafael.features.program.presentation.state.ProgramGenerateEvent
import dev.rafael.features.program.presentation.state.ProgramGenerateState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ProgramGenerateViewModel(
    private val repository: ProgramRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ProgramGenerateState())
    val state: StateFlow<ProgramGenerateState> = _state.asStateFlow()

    fun onEvent(event: ProgramGenerateEvent) {
        when (event) {
            ProgramGenerateEvent.Generate -> generate()
            ProgramGenerateEvent.DismissError -> _state.update { it.copy(error = null) }
        }
    }

    /** generatedId é evento one-shot — limpar após navegar evita re-disparo na recomposição. */
    fun consumeGeneratedId() {
        _state.update { it.copy(generatedId = null) }
    }

    private fun generate() {
        _state.update { it.copy(isGenerating = true, error = null) }
        viewModelScope.launch {
            when (val result = repository.generate()) {
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
