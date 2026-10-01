package dev.rafael.app.screens.progress

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.rafael.features.session.domain.HistoricoDeSessoes
import dev.rafael.features.stats.domain.Stats
import dev.rafael.contract.stats.UserStatsDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProgressState(
    val stats: UserStatsDto? = null,
    val carregandoInicial: Boolean = true,
    /** Arraste-pra-atualizar (G.6): true enquanto flush+sync roda, pra girar o indicador. */
    val sincronizando: Boolean = false,
)

/**
 * Progresso — OFFLINE-FIRST. As métricas vêm do cache local (`Flow`), então a tela pinta na hora
 * e funciona sem rede. O sync roda em paralelo e, quando grava, o `Flow` re-emite.
 *
 * Diferente do resto do app: aqui não existe estado de "erro de carregamento" — se a rede falhar,
 * a pessoa continua vendo os números dela. *Erro de rede não é erro de tela* quando há dado local.
 *
 * ## Por que ainda depende do histórico, mesmo sem mostrá-lo
 *
 * A lista saiu daqui no desmembramento (2026-10-01), mas o `flush()` ficou: **as métricas são
 * derivadas das sessões no SERVIDOR** (ARCH #16, autoridade do servidor). Pedir as métricas sem
 * subir antes o treino feito offline devolveria número velho, e a pessoa veria "1 treino" logo
 * depois de terminar o segundo.
 *
 * > **Quem depende de um número calculado lá fora tem de mandar o insumo antes de perguntar.**
 */
class ProgressViewModel(
    private val sessions: HistoricoDeSessoes,
    private val stats: Stats,
) : ViewModel() {

    private val _state = MutableStateFlow(ProgressState())
    val state: StateFlow<ProgressState> = _state.asStateFlow()

    init {
        // métricas do cache local: aparecem offline também
        stats.observar()
            .onEach { s -> _state.update { it.copy(stats = s, carregandoInicial = false) } }
            .launchIn(viewModelScope)
        sincronizar()
    }

    fun sincronizar() {
        viewModelScope.launch {
            _state.update { it.copy(sincronizando = true) }
            try {
                sessions.flush()      // sobe o treino feito offline ANTES de pedir as métricas
                stats.sincronizar()   // atualiza o cache de XP (o Flow re-emite)
            } finally {
                _state.update { it.copy(sincronizando = false) }
            }
        }
    }
}
