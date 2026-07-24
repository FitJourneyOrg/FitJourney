package dev.rafael.features.program.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.rafael.core.result.AppResult
import dev.rafael.features.program.domain.repository.ProgramRepository
import dev.rafael.features.program.presentation.state.ProgramListEvent
import dev.rafael.features.program.presentation.state.ProgramListState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ProgramListViewModel(
    private val repository: ProgramRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ProgramListState())
    val state: StateFlow<ProgramListState> = _state.asStateFlow()

    init { load() }

    fun onEvent(event: ProgramListEvent) {
        when (event) {
            ProgramListEvent.Load, ProgramListEvent.Retry -> load()
            is ProgramListEvent.CreateManual -> createManual(event.name)
        }
    }

    /**
     * createdId é EVENTO, não estado. Depois que a tela navega pro detalhe, precisa
     * ser limpo — senão, ao voltar, o LaunchedEffect(createdId) re-dispara na
     * recomposição e empurra o usuário pro detalhe de novo (bug do "voltar" que
     * exigia vários toques).
     */
    fun consumeCreatedId() {
        _state.update { it.copy(createdId = null) }
    }

    private fun load() {
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            when (val result = repository.list()) {
                is AppResult.Success ->
                    _state.update { it.copy(isLoading = false, programs = result.value) }
                is AppResult.Failure ->
                    _state.update { it.copy(isLoading = false, error = result.error.message) }
            }
        }
    }

    private fun createManual(name: String) {
        if (name.isBlank()) return
        _state.update { it.copy(isCreating = true, error = null) }
        viewModelScope.launch {
            when (val result = repository.createManual(name)) {
                is AppResult.Success ->
                    _state.update { it.copy(isCreating = false, createdId = result.value.id) }
                is AppResult.Failure ->
                    _state.update { it.copy(isCreating = false, error = result.error.message) }
            }
        }
    }
}
