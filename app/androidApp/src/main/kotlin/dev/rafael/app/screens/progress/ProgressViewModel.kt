package dev.rafael.app.screens.progress

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.rafael.contract.stats.ProgressDto
import dev.rafael.contract.stats.UserStatsDto
import dev.rafael.features.program.domain.model.Program
import dev.rafael.features.program.domain.repository.ProgramRepository
import dev.rafael.features.session.domain.HistoricoDeSessoes
import dev.rafael.features.stats.domain.FiltroDeProgresso
import dev.rafael.features.stats.domain.Progresso
import dev.rafael.features.stats.domain.Stats
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProgressState(
    val stats: UserStatsDto? = null,
    val analise: ProgressDto? = null,
    /** Os programas que o usuario tem — a fonte do NOME de cada chip (V48, derivado). */
    val programas: List<Program> = emptyList(),
    val filtro: FiltroDeProgresso = FiltroDeProgresso.Todos,
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

    /**
     * Recorte que ainda nao chegou: o cache e POR FILTRO, entao trocar de chip mostra nulo ate a
     * rede responder. Sem isso a tela diria "voce nao treinou nisso" para um recorte que ela
     * simplesmente ainda nao baixou.
     */
    val carregandoRecorte: Boolean get() = analise == null && !carregandoInicial
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
@OptIn(ExperimentalCoroutinesApi::class)
class ProgressViewModel(
    private val sessions: HistoricoDeSessoes,
    private val stats: Stats,
    private val progresso: Progresso,
    private val programas: ProgramRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ProgressState())
    val state: StateFlow<ProgressState> = _state.asStateFlow()

    /** O recorte escolhido. Fonte do que se OBSERVA, nao so do que se pede. */
    private val filtro = MutableStateFlow<FiltroDeProgresso>(FiltroDeProgresso.Todos)

    init {
        stats.observar()
            .onEach { s -> _state.update { it.copy(stats = s, carregandoInicial = false) } }
            .launchIn(viewModelScope)

        // `flatMapLatest` e nao um `observar()` fixo: o cache e por filtro, entao trocar de chip
        // troca a CHAVE observada. Observar uma so e atualizar na mao faria a tela mostrar o
        // recorte anterior ate a rede responder.
        filtro
            .flatMapLatest { f -> progresso.observar(f) }
            .onEach { a -> _state.update { it.copy(analise = a, carregandoInicial = false) } }
            .launchIn(viewModelScope)

        // Os programas vem do cache local (offline-first): os chips aparecem sem rede.
        programas.observePrograms()
            .onEach { p -> _state.update { it.copy(programas = p) } }
            .launchIn(viewModelScope)

        sincronizar()
    }

    /**
     * Troca o recorte. Sem `forcar`: o TTL e POR recorte, entao voltar a um chip ja visto usa o
     * cache em vez de ir a rede de novo — que e o comportamento que faz alternar entre dois
     * programas parecer instantaneo.
     */
    fun selecionar(novo: FiltroDeProgresso) {
        if (filtro.value == novo) return
        filtro.value = novo
        _state.update { it.copy(filtro = novo) }
        viewModelScope.launch {
            _state.update { it.copy(sincronizando = true) }
            try {
                progresso.sincronizar(novo)
            } finally {
                _state.update { it.copy(sincronizando = false) }
            }
        }
    }

    fun sincronizar() {
        viewModelScope.launch {
            _state.update { it.copy(sincronizando = true) }
            try {
                sessions.flush()                      // sobe o treino offline ANTES de perguntar
                stats.sincronizar(forcar = true)
                progresso.sincronizar(filtro.value, forcar = true)
            } finally {
                _state.update { it.copy(sincronizando = false) }
            }
        }
    }
}
