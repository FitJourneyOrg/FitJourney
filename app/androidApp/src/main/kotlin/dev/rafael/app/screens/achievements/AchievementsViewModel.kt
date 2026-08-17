package dev.rafael.app.screens.achievements

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.rafael.app.data.achievements.Achievements
import dev.rafael.contract.stats.AchievementDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AchievementsState(
    val conquistas: List<AchievementDto> = emptyList(),
    val carregandoInicial: Boolean = true,
) {
    val desbloqueadas: List<AchievementDto> get() = conquistas.filter { it.unlocked }
    val bloqueadas: List<AchievementDto> get() = conquistas.filterNot { it.unlocked }

    /** "3 de 9" no topo — dá a dimensão do que falta sem obrigar a contar a grade. */
    val total: Int get() = conquistas.size
}

/**
 * Conquistas — OFFLINE-FIRST, como o Progresso.
 *
 * Não existe estado de erro aqui, de propósito: a grade vem do cache local, então a rede
 * falhar não tem consequência visível. Erro de rede não é erro de tela quando há dado local
 * (ARCH #31, nível 1: silêncio).
 */
class AchievementsViewModel(
    private val achievements: Achievements,
) : ViewModel() {

    private val _state = MutableStateFlow(AchievementsState())
    val state: StateFlow<AchievementsState> = _state.asStateFlow()

    init {
        achievements.observar()
            .onEach { lista ->
                _state.update { it.copy(conquistas = lista, carregandoInicial = false) }
            }
            .launchIn(viewModelScope)
        sincronizar()
    }

    /**
     * @param forcar use ao voltar de um treino: o progresso mudou e a janela de 2 min do TTL
     * seguraria justamente a medalha que o usuário acabou de merecer.
     */
    fun sincronizar(forcar: Boolean = false) {
        viewModelScope.launch { achievements.sincronizar(forcar) }
    }
}
