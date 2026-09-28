package dev.rafael.features.program.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.rafael.core.result.AppError
import dev.rafael.core.result.AppResult
import dev.rafael.features.program.domain.model.ProgramScheduleEntry
import dev.rafael.features.program.domain.repository.ProgramRepository
import dev.rafael.features.program.presentation.state.ProgramDetailEvent
import dev.rafael.features.program.presentation.state.ProgramDetailState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
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

    // Sem init { load() }: a tela dispara Retry no ON_RESUME (1ª entrada + refresh ao voltar
    // do paywall). Ter os dois causava GET /programs duplicado ao abrir o detalhe.

    init {
        // Selo de pendente (ARCH #30, B.4), migrado do ProgramListViewModel junto com a
        // ativação: reativo, some sozinho quando o worker sincroniza.
        repository.observarPendentes()
            .onEach { p -> _state.update { it.copy(pendencias = p) } }
            .launchIn(viewModelScope)
    }

    fun onEvent(event: ProgramDetailEvent) {
        when (event) {
            ProgramDetailEvent.Retry -> load()
            is ProgramDetailEvent.Rename -> rename(event.name)
            ProgramDetailEvent.Delete -> delete()
            is ProgramDetailEvent.SetWorkoutDay -> setDay(event.workoutId, event.dayOfWeek)
            is ProgramDetailEvent.Activate -> activate(event.workoutId)
            is ProgramDetailEvent.Descartar -> descartar(event.alvoId)
        }
    }

    /**
     * Define o dia da semana de um treino e persiste (PUT /schedule). Monta a agenda completa
     * a partir do schedule atual, troca só o dia do treino alvo, e manda tudo. Se o dia já
     * estiver ocupado por outro treino, faz swap (troca os dias) — mantém dias distintos.
     */
    private fun setDay(workoutId: String, dayOfWeek: Int) {
        val program = _state.value.program ?: return
        val currentDays = program.schedule.associate { it.workoutId to it.dayOfWeek }.toMutableMap()
        val oldDay = currentDays[workoutId] ?: return
        if (oldDay == dayOfWeek) return
        // se o dia destino já é de outro treino, troca (swap) pra manter distintos
        currentDays.entries.firstOrNull { it.value == dayOfWeek && it.key != workoutId }?.let {
            currentDays[it.key] = oldDay
        }
        currentDays[workoutId] = dayOfWeek
        val schedule = currentDays.map { (wId, day) -> ProgramScheduleEntry(workoutId = wId, dayOfWeek = day) }
        _state.update { it.copy(isReordering = true, error = null) }
        viewModelScope.launch {
            when (val result = repository.setSchedule(programId, schedule)) {
                is AppResult.Success ->
                    _state.update { it.copy(isReordering = false, program = result.value) }
                is AppResult.Failure ->
                    _state.update { it.copy(isReordering = false, error = result.error) }
            }
        }
    }

    private fun load(forcar: Boolean = false) {
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            when (val result = if (forcar) repository.refresh() else repository.list()) {
                is AppResult.Success -> {
                    val found = result.value.firstOrNull { it.id == programId }
                    _state.update {
                        it.copy(
                            isLoading = false,
                            program = found,
                            error = if (found == null) AppError.NotFound("Programa não encontrado") else null,
                        )
                    }
                }
                is AppResult.Failure ->
                    _state.update { it.copy(isLoading = false, error = result.error) }
            }
        }
    }

    /**
     * V59, migrado do ProgramListViewModel: ONLINE-ONLY (ver
     * [dev.rafael.features.program.domain.repository.ProgramRepository.activateWorkout]).
     * Sucesso força um `load(forcar = true)` pra tela já mostrar o `isActive` novo, sem
     * esperar o próximo ON_RESUME.
     */
    private fun activate(workoutId: String) {
        _state.update { it.copy(activating = workoutId, error = null) }
        viewModelScope.launch {
            when (val result = repository.activateWorkout(workoutId)) {
                is AppResult.Success -> {
                    _state.update { it.copy(activating = null) }
                    load(forcar = true)
                }
                is AppResult.Failure ->
                    _state.update { it.copy(activating = null, error = result.error) }
            }
        }
    }

    /**
     * Só sai da fila LOCALMENTE (nunca falha) -- o `load(forcar = true)` que segue é quem busca
     * a verdade do servidor e sobrescreve a tentativa recusada (ver KDoc do repositório).
     */
    private fun descartar(alvoId: String) {
        viewModelScope.launch {
            repository.descartarPendencia(alvoId)
            load(forcar = true)
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
                    _state.update { it.copy(isRenaming = false, error = result.error) }
            }
        }
    }

    private fun delete() {
        viewModelScope.launch {
            when (val result = repository.delete(programId)) {
                is AppResult.Success -> _state.update { it.copy(isDeleted = true) }
                is AppResult.Failure -> _state.update { it.copy(error = result.error) }
            }
        }
    }
}
