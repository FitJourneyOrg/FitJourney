package dev.rafael.app.screens.progress

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.rafael.app.data.session.SessaoLocal
import dev.rafael.app.data.session.SessionSync
import dev.rafael.app.data.stats.StatsRepository
import dev.rafael.contract.stats.UserStatsDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProgressState(
    val historico: List<SessaoLocal> = emptyList(),
    val stats: UserStatsDto? = null,
    val carregandoInicial: Boolean = true,
)

/**
 * Progresso — OFFLINE-FIRST. A lista vem SEMPRE do banco local (Flow), então a tela pinta
 * na hora e funciona sem rede. O sync com o servidor roda em paralelo e, quando grava,
 * o Flow re-emite e a tela se atualiza sozinha.
 *
 * Diferente do resto do app: aqui não existe estado de "erro de carregamento" — se a rede
 * falhar, o usuário continua vendo o histórico dele. Erro de rede não é erro de tela.
 */
class ProgressViewModel(
    private val sessions: SessionSync,
    private val stats: StatsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ProgressState())
    val state: StateFlow<ProgressState> = _state.asStateFlow()

    init {
        sessions.observarHistorico()
            .onEach { lista ->
                _state.update { it.copy(historico = lista, carregandoInicial = false) }
            }
            .launchIn(viewModelScope)
        // métricas do cache local: aparecem offline também
        stats.observar()
            .onEach { s -> _state.update { it.copy(stats = s) } }
            .launchIn(viewModelScope)
        sincronizar()
    }

    fun sincronizar() {
        viewModelScope.launch {
            sessions.flush()                  // sobe o que foi feito offline
            sessions.sincronizarHistorico()   // desce o que falta (o Flow re-emite)
            stats.sincronizar()               // atualiza o cache de XP (o Flow re-emite)
        }
    }
}
