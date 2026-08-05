package dev.rafael.app.screens.reveal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.rafael.core.result.AppResult
import dev.rafael.features.program.domain.model.Program
import dev.rafael.features.program.domain.repository.ProgramRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProgramRevealState(
    val program: Program? = null,
    val isGenerating: Boolean = true,     // montando o 1º programa
    val error: String? = null,
) {
    /** Programa IA de usuário free vem trancado (blur #23). Destranca após assinar. */
    val locked: Boolean get() = program?.locked == true
}

/**
 * Tela de revelação do onboarding (Fase 7). Gera o 1º programa e mostra Dia 1 grátis + dias
 * trancados. O "desbloquear" leva pra página de Paywall (a compra mora lá). Ao voltar do
 * Paywall, `reload()` (no onResume) re-busca o MESMO programa — já desbloqueado se assinou.
 */
class ProgramRevealViewModel(
    private val programs: ProgramRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ProgramRevealState())
    val state: StateFlow<ProgramRevealState> = _state.asStateFlow()

    private var programId: String? = null

    init { generate() }

    fun retry() = generate()

    private fun generate() {
        _state.update { it.copy(isGenerating = true, error = null) }
        viewModelScope.launch {
            when (val r = programs.generate()) {
                is AppResult.Success -> {
                    programId = r.value.id
                    _state.update { it.copy(isGenerating = false, program = r.value) }
                }
                is AppResult.Failure ->
                    _state.update { it.copy(isGenerating = false, error = r.error.message) }
            }
        }
    }

    /** Re-busca o programa gerado (após voltar do Paywall). No-op se ainda não gerou. */
    fun reload() {
        val id = programId ?: return
        viewModelScope.launch {
            val r = programs.list()
            if (r is AppResult.Success) {
                r.value.firstOrNull { it.id == id }?.let { fresh ->
                    _state.update { it.copy(program = fresh) }
                }
            }
        }
    }
}
