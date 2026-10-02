package dev.rafael.app.screens.progress

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.rafael.contract.stats.ProgressDto
import dev.rafael.contract.stats.UserStatsDto
import dev.rafael.features.session.domain.HistoricoDeSessoes
import dev.rafael.features.stats.domain.Progresso
import dev.rafael.features.stats.domain.Stats
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProgressState(
    val stats: UserStatsDto? = null,
    val analise: ProgressDto? = null,
    val carregandoInicial: Boolean = true,
    /** Arraste-pra-atualizar (G.6): true enquanto flush+sync roda, pra girar o indicador. */
    val sincronizando: Boolean = false,
) {
    /**
     * Os blocos pagos vieram nulos POR PLANO, e nao por falta de dado.
     *
     * A distincao decide entre paywall e estado vazio, e errar nela mostraria o paywall a quem
     * acabou de assinar. Quem responde e o servidor, no `analysisLocked` — a tela nunca deduz
     * isso de um nulo.
     */
    val trancado: Boolean get() = analise?.analysisLocked == true

    /** Nao ha o que desenhar: a pessoa so treina peso corporal, ou ainda nao treinou. */
    val semCarga: Boolean get() = analise != null && analise.sinceDate == null
}

/**
 * Progresso — OFFLINE-FIRST. Metricas e analise vem do CACHE LOCAL (`Flow`), entao a tela pinta
 * na hora e funciona sem rede. O sync roda em paralelo e, quando grava, os `Flow` re-emitem.
 *
 * Diferente do resto do app: aqui nao existe estado de "erro de carregamento" — se a rede falhar,
 * a pessoa continua vendo os numeros dela. *Erro de rede nao e erro de tela* quando ha dado local.
 *
 * ## Duas portas, nao uma
 *
 * [Stats] e lido pela Home, pelo menu e pelo perfil em todo `onResume`; a [Progresso] varre o
 * historico inteiro e so interessa a esta tela. Juntar as duas faria toda abertura da Home pagar
 * pelo grafico que ela nao desenha.
 *
 * ## Por que o `flush()` vem ANTES, e agora importa mais
 *
 * A lista de historico saiu daqui no desmembramento (2026-10-01), mas o `flush()` ficou: as
 * metricas e a analise sao derivadas das sessoes no SERVIDOR (ARCH #16). Pedir sem subir antes o
 * treino feito offline devolveria numero velho — e com a J.2 isso ficou visivel, porque o grafico
 * da semana apareceria sem o treino que a pessoa acabou de terminar.
 *
 * > **Quem depende de um numero calculado la fora tem de mandar o insumo antes de perguntar.**
 *
 * Por isso os dois sincronizam com `forcar = true` depois do flush: o TTL de dois minutos existe
 * para a troca de abas, nao para o momento em que sabemos que o dado mudou.
 */
class ProgressViewModel(
    private val sessions: HistoricoDeSessoes,
    private val stats: Stats,
    private val progresso: Progresso,
) : ViewModel() {

    private val _state = MutableStateFlow(ProgressState())
    val state: StateFlow<ProgressState> = _state.asStateFlow()

    init {
        stats.observar()
            .onEach { s -> _state.update { it.copy(stats = s, carregandoInicial = false) } }
            .launchIn(viewModelScope)

        progresso.observar()
            .onEach { a -> _state.update { it.copy(analise = a, carregandoInicial = false) } }
            .launchIn(viewModelScope)

        sincronizar()
    }

    fun sincronizar() {
        viewModelScope.launch {
            _state.update { it.copy(sincronizando = true) }
            try {
                sessions.flush()                      // sobe o treino offline ANTES de perguntar
                stats.sincronizar(forcar = true)
                progresso.sincronizar(forcar = true)
            } finally {
                _state.update { it.copy(sincronizando = false) }
            }
        }
    }
}
