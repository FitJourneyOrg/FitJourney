package dev.rafael.app.screens.progress

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.rafael.contract.stats.ExercicioDetalheDto
import dev.rafael.core.result.AppError
import dev.rafael.core.result.AppResult
import dev.rafael.features.stats.domain.DetalheDeExercicio
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ExercicioDetalheState(
    val detalhe: ExercicioDetalheDto? = null,
    val carregando: Boolean = true,
    val erro: AppError? = null,
)

/**
 * Detalhe de UM exercicio dentro de UM programa (J.5) — a tela que "ver detalhado" abre a
 * partir da lista de exercicios do recorte de programa.
 *
 * ONLINE-ONLY, sem cache — ver KDoc de [DetalheDeExercicio]. E uma tela de consulta, aberta uma
 * vez por toque; diferente do Progresso, nao ha `onResume` que justifique guardar o ultimo
 * valor conhecido.
 */
class ExercicioDetalheViewModel(
    private val detalhes: DetalheDeExercicio,
) : ViewModel() {

    private val _state = MutableStateFlow(ExercicioDetalheState())
    val state: StateFlow<ExercicioDetalheState> = _state.asStateFlow()

    fun carregar(programId: String, exercicioId: String) {
        _state.update { it.copy(carregando = true, erro = null) }
        viewModelScope.launch {
            when (val r = detalhes.buscar(programId, exercicioId)) {
                is AppResult.Success -> _state.update { it.copy(detalhe = r.value, carregando = false) }
                is AppResult.Failure -> _state.update { it.copy(erro = r.error, carregando = false) }
            }
        }
    }
}
