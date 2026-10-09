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
import dev.rafael.features.stats.domain.SelecaoDeExercicios
import dev.rafael.features.stats.domain.Stats
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * O que a tela precisa para DESENHAR OS CONTROLES, preservado entre recortes.
 *
 * ## Por que nao le isso direto do [ProgressDto]
 *
 * O cache e por recorte, entao escolher um recorte novo da **cache miss garantido** e o `analise`
 * fica nulo ate a rede responder. Controle que le o `analise` **desaparece no instante em que a
 * pessoa o usa** e volta depois, com o layout saltando — o chip sumindo ao ser tocado, o slider
 * sumindo sob o dedo. Entao a parte ESTRUTURAL (quais recortes existem, quantas semanas o
 * programa tem) sobrevive do ultimo dado que chegou.
 *
 * Os NUMEROS continuam saindo do `analise`: lembrar tonelagem de um recorte enquanto outro
 * carrega seria mostrar dado errado, nao controle estavel.
 *
 * @param programaMedido de QUAL programa [semanas] fala. Sem isso, trocar do programa de 12
 *   semanas para um de 8 desenharia o slider do segundo com o teto do primeiro durante o
 *   carregamento — e a pessoa poderia escolher a semana 11 de um programa que tem 8.
 */
data class EstruturaDoFiltro(
    val programas: List<String> = emptyList(),
    val temAvulsos: Boolean = false,
    val programaMedido: String? = null,
    val semanas: Int? = null,
    /**
     * A janela de calendario APLICADA, vinda do servidor. Preservada entre recortes pelo mesmo
     * motivo do resto: o chip nao pode sumir no instante em que e tocado.
     */
    val janela: Int? = null,
    val de: Int? = null,
    val ate: Int? = null,
)

data class ProgressState(
    val stats: UserStatsDto? = null,
    val analise: ProgressDto? = null,
    /** Os programas que o usuario tem — a fonte do NOME de cada chip (V48, derivado). */
    val programas: List<Program> = emptyList(),
    val filtro: FiltroDeProgresso = FiltroDeProgresso.Todos(),
    /** Quais exercicios ganham linha. Vazia = os tres que o servidor escolheu. */
    val selecao: SelecaoDeExercicios = SelecaoDeExercicios.PADRAO,
    /** Ver [EstruturaDoFiltro]: sobrevive ao nulo entre recortes, para o controle nao piscar. */
    val estrutura: EstruturaDoFiltro = EstruturaDoFiltro(),
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
    private val filtro = MutableStateFlow<FiltroDeProgresso>(FiltroDeProgresso.Todos())

    /** Ver [SelecaoDeExercicios]. Eixo proprio: qualquer recorte aceita qualquer selecao. */
    private val selecao = MutableStateFlow(SelecaoDeExercicios.PADRAO)

    init {
        stats.observar()
            .onEach { s -> _state.update { it.copy(stats = s, carregandoInicial = false) } }
            .launchIn(viewModelScope)

        // `flatMapLatest` e nao um `observar()` fixo: o cache e por filtro, entao trocar de chip
        // troca a CHAVE observada. Observar uma so e atualizar na mao faria a tela mostrar o
        // recorte anterior ate a rede responder.
        combine(filtro, selecao) { f, s -> f to s }
            // O `map` carrega o filtro JUNTO do dado em vez de reler `filtro.value` depois: a
            // emissao pertence ao recorte que a produziu, e ler o estado atual atribuiria o dado
            // ao filtro errado se a pessoa trocasse de chip no meio do caminho.
            .flatMapLatest { (f, s) -> progresso.observar(f, s).map { a -> f to a } }
            .onEach { (f, a) ->
                _state.update {
                    it.copy(
                        analise = a,
                        // `?: it.estrutura`: o nulo do recorte em voo NAO apaga os controles.
                        estrutura = a?.let { d -> estruturaDe(d, f) } ?: it.estrutura,
                        carregandoInicial = false,
                    )
                }
            }
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
        // ⚠️ A SELECAO RESETA. Os exercicios do Programa A nao sao os do B, e levar ids que nao
        // existem no recorte novo faria o servidor descarta-los e cair no padrao — o grafico
        // mudaria sozinho sem explicar por que. Recorte novo abre com os tres mais relevantes
        // DELE, que e o que a pessoa espera ver.
        selecao.value = SelecaoDeExercicios.PADRAO
        _state.update { it.copy(filtro = novo, selecao = SelecaoDeExercicios.PADRAO) }
        pedir(novo, SelecaoDeExercicios.PADRAO)
    }

    /**
     * Poe ou tira um exercicio do grafico.
     *
     * [visiveis] sao os que estao desenhados AGORA: com a selecao vazia quem escolheu foi o
     * servidor, e o primeiro toque precisa partir dali — senao ele levaria o grafico de tres
     * linhas para uma so, do nada. Ver [SelecaoDeExercicios.alternar].
     */
    fun alternarExercicio(id: String, visiveis: List<String>) {
        val nova = selecao.value.alternar(id, visiveis)
        if (nova == selecao.value) return   // toque que esvaziaria: ignorado
        selecao.value = nova
        _state.update { it.copy(selecao = nova) }
        pedir(filtro.value, nova)
    }

    private fun pedir(f: FiltroDeProgresso, s: SelecaoDeExercicios) {
        viewModelScope.launch {
            _state.update { it.copy(sincronizando = true) }
            try {
                progresso.sincronizar(f, s)
            } finally {
                _state.update { it.copy(sincronizando = false) }
            }
        }
    }

    /**
     * Troca so a FAIXA, mantendo o programa. Chamado quando o dedo SAI do slider, nunca durante
     * o arraste: cada valor intermediario e um recorte com chave de cache propria, entao aplicar
     * a cada pixel viraria dezenas de requisicoes e dezenas de entradas de cache por gesto.
     *
     * Ignora quando nao ha programa escolhido — faixa de semanas sem programa nao tem referente.
     */
    fun selecionarFaixa(de: Int, ate: Int) {
        val atual = filtro.value as? FiltroDeProgresso.DoPrograma ?: return
        selecionar(atual.copy(de = de, ate = ate))
    }

    private fun estruturaDe(d: ProgressDto, f: FiltroDeProgresso) = EstruturaDoFiltro(
        programas = d.availablePrograms,
        temAvulsos = d.hasUnassigned,
        programaMedido = (f as? FiltroDeProgresso.DoPrograma)?.programId,
        semanas = d.programWeeks,
        de = d.fromWeek,
        ate = d.toWeek,
        janela = d.weeksWindow,
    )

    /**
     * Troca a JANELA de calendario, mantendo o recorte.
     *
     * No recorte de programa e no-op: lá a faixa e a janela, e o controle nem aparece. O
     * [FiltroDeProgresso.comJanela] devolve `this` nesse caso, e o `selecionar` descarta por
     * igualdade — entao nao ha requisicao a toa nem estado invalido possivel.
     */
    fun selecionarJanela(semanas: Int) = selecionar(filtro.value.comJanela(semanas))

    fun sincronizar() {
        viewModelScope.launch {
            _state.update { it.copy(sincronizando = true) }
            try {
                sessions.flush()                      // sobe o treino offline ANTES de perguntar
                stats.sincronizar(forcar = true)
                progresso.sincronizar(filtro.value, selecao.value, forcar = true)
            } finally {
                _state.update { it.copy(sincronizando = false) }
            }
        }
    }
}
