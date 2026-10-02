package dev.rafael.app.screens.historico

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.rafael.features.session.domain.HistoricoDeSessoes
import dev.rafael.features.session.domain.SessaoLocal
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HistoricoState(
    val sessoes: List<SessaoLocal> = emptyList(),
    val carregandoInicial: Boolean = true,
    /** Arraste-pra-atualizar: true enquanto flush+sync roda, pra girar o indicador. */
    val sincronizando: Boolean = false,
)

/**
 * O histórico de treinos, agora em tela própria (desmembramento do Progresso, 2026-10-01).
 *
 * OFFLINE-FIRST: a lista vem SEMPRE do banco local (`Flow`), então pinta na hora e funciona sem
 * rede. O sync roda em paralelo e, quando grava, o `Flow` re-emite e a tela se atualiza sozinha.
 *
 * Como no Progresso, **não existe estado de erro de carregamento**: se a rede falhar, a pessoa
 * continua vendo o histórico dela. *Erro de rede não é erro de tela* quando há dado local.
 */
class HistoricoViewModel(
    private val sessions: HistoricoDeSessoes,
) : ViewModel() {

    private val _state = MutableStateFlow(HistoricoState())
    val state: StateFlow<HistoricoState> = _state.asStateFlow()

    init {
        sessions.observarHistorico()
            .onEach { lista -> _state.update { it.copy(sessoes = lista, carregandoInicial = false) } }
            .launchIn(viewModelScope)
        sincronizar()
    }

    fun sincronizar() {
        viewModelScope.launch {
            _state.update { it.copy(sincronizando = true) }
            try {
                sessions.flush()                  // sobe o que foi feito offline
                sessions.sincronizarHistorico()   // desce o que falta (o Flow re-emite)
            } finally {
                _state.update { it.copy(sincronizando = false) }
            }
        }
    }
}
