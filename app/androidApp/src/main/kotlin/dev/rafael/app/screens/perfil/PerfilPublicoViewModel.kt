package dev.rafael.app.screens.perfil

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.rafael.app.data.perfil.PerfisPublicos
import dev.rafael.contract.user.PublicProfileDto
import dev.rafael.core.result.AppError
import dev.rafael.core.result.AppResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PerfilPublicoState(
    val perfil: PublicProfileDto? = null,
    val carregando: Boolean = true,
    val erro: AppError? = null,
)

/**
 * Perfil de OUTRA pessoa (C.1).
 *
 * **Sem `combine` de três fontes, como o [PerfilViewModel] do dono.** Lá o estado nasce de
 * `Me` + `Stats` + `Achievements`, todos cache-first, porque a tela do dono precisa abrir
 * offline. Aqui é uma requisição só, online, e o estado tem `carregando`/`erro` explícitos —
 * porque falhar é um desfecho normal desta tela e não do outro.
 */
class PerfilPublicoViewModel(private val perfis: PerfisPublicos) : ViewModel() {

    private val _state = MutableStateFlow(PerfilPublicoState())
    val state: StateFlow<PerfilPublicoState> = _state.asStateFlow()

    private var jaCarregado: String? = null

    /**
     * Idempotente por `userId`: chamada de novo com o mesmo id no `ON_START` (volta da pilha,
     * rotação) não refaz a requisição. Recarregar de propósito é o [recarregar].
     */
    fun carregar(userId: String) {
        if (jaCarregado == userId) return
        jaCarregado = userId
        buscar(userId)
    }

    fun recarregar() {
        jaCarregado?.let { buscar(it) }
    }

    private fun buscar(userId: String) {
        _state.update { it.copy(carregando = true, erro = null) }
        viewModelScope.launch {
            when (val r = perfis.de(userId)) {
                is AppResult.Success ->
                    _state.update { it.copy(perfil = r.value, carregando = false, erro = null) }
                is AppResult.Failure ->
                    // O perfil anterior fica na tela de propósito quando já havia um: recarregar
                    // e falhar não deve apagar o que a pessoa está lendo.
                    _state.update { it.copy(carregando = false, erro = r.error) }
            }
        }
    }
}
