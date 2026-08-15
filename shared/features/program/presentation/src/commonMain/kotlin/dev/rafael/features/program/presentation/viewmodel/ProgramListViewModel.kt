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
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ProgramListViewModel(
    private val repository: ProgramRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ProgramListState())
    val state: StateFlow<ProgramListState> = _state.asStateFlow()

    init {
        // ARCH #30: a lista vem do BANCO LOCAL e é reativa — pinta na hora, offline inclusive,
        // e se atualiza sozinha quando o sync grava. O `load()` abaixo é só sincronização.
        repository.observePrograms()
            .onEach { lista -> _state.update { it.copy(programs = lista, isLoading = false) } }
            .launchIn(viewModelScope)

        // Carimbo persistido: responde "já baixei alguma vez aqui?" mesmo em cold start
        // offline, quando nenhum sync desta sessão teve chance de acontecer.
        viewModelScope.launch {
            val ja = repository.jaSincronizou()
            _state.update { it.copy(sincronizouAlgumaVez = ja) }
        }
    }

    fun onEvent(event: ProgramListEvent) {
        when (event) {
            ProgramListEvent.Load -> load(forcar = false)   // cache-first: trocar de aba não vai à rede
            ProgramListEvent.Retry -> load(forcar = true)   // o usuário pediu de novo: força a rede
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

    /** Erro de ação é one-shot (snackbar). Sem limpar, ele reaparece a cada recomposição. */
    fun consumeError() {
        _state.update { it.copy(error = null) }
    }

    /**
     * SÓ sincroniza — a lista já é observada do banco. Falha de rede não vira erro vermelho na
     * tela: se há dado local, o usuário nem percebe; se não há, a tela mostra "sem conexão"
     * (que é diferente de "você não tem programas").
     */
    private fun load(forcar: Boolean = false) {
        _state.update { it.copy(isLoading = _state.value.programs.isEmpty(), erroSync = null) }
        viewModelScope.launch {
            when (val result = if (forcar) repository.refresh() else repository.list()) {
                is AppResult.Success ->
                    _state.update {
                        it.copy(isLoading = false, programs = result.value, sincronizouAlgumaVez = true)
                    }
                is AppResult.Failure ->
                    _state.update { it.copy(isLoading = false, erroSync = result.error) }
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
                    _state.update { it.copy(isCreating = false, error = result.error) }
            }
        }
    }
}
