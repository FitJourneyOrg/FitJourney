package dev.rafael.features.exercise.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.rafael.core.result.AppResult
import dev.rafael.features.exercise.domain.model.FiltroDeExercicios
import dev.rafael.features.exercise.domain.repository.ExerciseRepository
import dev.rafael.features.exercise.presentation.state.ExerciseListEvent
import dev.rafael.features.exercise.presentation.state.ExerciseListState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class ExerciseListViewModel(
    private val repository: ExerciseRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ExerciseListState())
    val state: StateFlow<ExerciseListState> = _state.asStateFlow()

    /**
     * O filtro CONSULTADO — distinto do que está no [_state], que acompanha a digitação.
     *
     * Era um `Job` cancelado à mão a cada troca de chip. `flatMapLatest` faz o mesmo cancelamento,
     * só que sem ninguém precisar lembrar: assinatura nova chega, a anterior morre.
     */
    private val filtro = MutableStateFlow(FiltroDeExercicios())

    init {
        filtro
            // ⭐ Debounce DINÂMICO, e a assimetria é o ponto: digitar emite uma consulta por
            // tecla, e cada uma relê 923 linhas do SQLite. Tocar num chip emite UMA, e fazer o
            // chip esperar um quarto de segundo faria a tela parecer travada.
            //
            // > **Espera-se quem digita, não quem toca.**
            .debounce { if (it.busca.isBlank()) 0L else DEBOUNCE_BUSCA_MS }
            .flatMapLatest { repository.observeExercises(it) }
            .onEach { lista -> _state.update { it.copy(exercises = lista) } }
            .launchIn(viewModelScope)

        // Sincronização de fundo, com TTL de 24h no repositório. Antes isto era "network-first"
        // e baixava os 965 exercícios em TODA entrada na aba.
        refresh(forcar = false)
    }

    fun onEvent(event: ExerciseListEvent) {
        when (event) {
            is ExerciseListEvent.CategorySelected -> {
                _state.update { it.copy(selectedCategory = event.category) }
                filtro.update { it.copy(categoria = event.category) }
            }
            is ExerciseListEvent.MuscleGroupSelected -> {
                _state.update { it.copy(selectedMuscleGroup = event.muscleGroup) }
                filtro.update { it.copy(musculo = event.muscleGroup) }
            }
            is ExerciseListEvent.BuscaAlterada -> {
                // O campo responde na TECLA; a consulta é que espera. Ligar o campo ao fluxo
                // debounced faria a letra aparecer um quarto de segundo depois de digitada.
                _state.update { it.copy(busca = event.texto) }
                filtro.update { it.copy(busca = event.texto) }
            }
            ExerciseListEvent.Refresh -> refresh(forcar = true)   // o usuário pediu: fura o TTL
        }
    }

    /**
     * A tela mostrou a falha (snackbar) e a descarta. Sem limpar o erro no state o aviso
     * reapareceria a cada recomposição. Não mexe no acervo: só o aviso some.
     */
    fun consumeError() = _state.update { it.copy(error = null) }

    private fun refresh(forcar: Boolean) {
        _state.update { it.copy(isRefreshing = true, error = null) }
        viewModelScope.launch {
            when (val result = repository.refresh(forcar)) {
                is AppResult.Success ->
                    _state.update { it.copy(isRefreshing = false) }   // banco atualiza a lista sozinho
                is AppResult.Failure ->
                    _state.update { it.copy(isRefreshing = false, error = result.error) }
            }
        }
    }

    private companion object {
        /** Um quarto de segundo: longo o bastante para não consultar por tecla, curto o bastante
         *  para a lista parecer acompanhar quem digita. */
        const val DEBOUNCE_BUSCA_MS = 250L
    }
}
