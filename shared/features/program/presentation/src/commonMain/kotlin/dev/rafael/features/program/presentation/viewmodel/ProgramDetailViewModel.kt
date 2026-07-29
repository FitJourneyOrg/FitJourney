package dev.rafael.features.program.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.rafael.core.result.AppResult
import dev.rafael.features.program.domain.repository.ProgramRepository
import dev.rafael.features.program.presentation.state.ProgramDetailEvent
import dev.rafael.features.program.presentation.state.ProgramDetailState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Não existe GET /programs/{id} — reusa list() e filtra pelo id (listas são
 * pequenas, 0-2 itens no plano grátis). Se o teto premium crescer muito, criar
 * GET /programs/{id} vira débito a resolver.
 */
class ProgramDetailViewModel(
    private val programId: String,
    private val repository: ProgramRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ProgramDetailState())
    val state: StateFlow<ProgramDetailState> = _state.asStateFlow()

    init { load() }

    fun onEvent(event: ProgramDetailEvent) {
        when (event) {
            ProgramDetailEvent.Retry -> load()
            is ProgramDetailEvent.Rename -> rename(event.name)
            ProgramDetailEvent.Delete -> delete()
            is ProgramDetailEvent.MoveWorkout -> move(event.workoutId, event.up)
        }
    }

    /**
     * Reordena um treino um passo p/ cima/baixo e persiste (PUT /schedule). Monta a nova
     * ordem a partir da lista atual e manda os workoutIds; o server grava day_of_week=posição.
     */
    private fun move(workoutId: String, up: Boolean) {
        val current = _state.value.program?.workouts ?: return
        val ids = current.mapNotNull { it.id }.toMutableList()
        val i = ids.indexOf(workoutId)
        if (i < 0) return
        val j = if (up) i - 1 else i + 1
        if (j < 0 || j > ids.lastIndex) return          // já está na ponta
        ids[i] = ids[j].also { ids[j] = ids[i] }        // swap
        _state.update { it.copy(isReordering = true, error = null) }
        viewModelScope.launch {
            when (val result = repository.reorderSchedule(programId, ids)) {
                is AppResult.Success ->
                    _state.update { it.copy(isReordering = false, program = result.value) }
                is AppResult.Failure ->
                    _state.update { it.copy(isReordering = false, error = result.error.message) }
            }
        }
    }

    private fun load() {
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            when (val result = repository.list()) {
                is AppResult.Success -> {
                    val found = result.value.firstOrNull { it.id == programId }
                    _state.update {
                        it.copy(
                            isLoading = false,
                            program = found,
                            error = if (found == null) "Programa não encontrado" else null,
                        )
                    }
                }
                is AppResult.Failure ->
                    _state.update { it.copy(isLoading = false, error = result.error.message) }
            }
        }
    }

    private fun rename(name: String) {
        if (name.isBlank()) return
        _state.update { it.copy(isRenaming = true, error = null) }
        viewModelScope.launch {
            when (val result = repository.rename(programId, name)) {
                is AppResult.Success ->
                    _state.update { it.copy(isRenaming = false, program = result.value) }
                is AppResult.Failure ->
                    _state.update { it.copy(isRenaming = false, error = result.error.message) }
            }
        }
    }

    private fun delete() {
        viewModelScope.launch {
            when (val result = repository.delete(programId)) {
                is AppResult.Success -> _state.update { it.copy(isDeleted = true) }
                is AppResult.Failure -> _state.update { it.copy(error = result.error.message) }
            }
        }
    }
}
