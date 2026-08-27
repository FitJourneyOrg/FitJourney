package dev.rafael.app.screens.grupos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.rafael.app.data.checkin.CheckIns
import dev.rafael.app.data.groups.Groups
import dev.rafael.contract.checkin.CheckInDto
import dev.rafael.contract.group.GroupDto
import dev.rafael.contract.group.RankingEntryDto
import dev.rafael.contract.group.GroupMemberDto
import dev.rafael.contract.group.MemberRole
import dev.rafael.core.result.AppError
import dev.rafael.core.result.AppResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

data class GrupoDetalheState(
    val grupo: GroupDto? = null,
    val membros: List<GroupMemberDto> = emptyList(),
    /** O FEED (8.0): os check-ins do grupo, mais recente primeiro. */
    val feed: List<CheckInDto> = emptyList(),
    val carregandoFeed: Boolean = true,
    val carregandoMais: Boolean = false,

    /** O RANKING (7.2). Posição e desempate vêm resolvidos do servidor. */
    val ranking: List<RankingEntryDto> = emptyList(),
    val carregandoRanking: Boolean = true,

    /**
     * A última página veio cheia, então provavelmente há mais.
     *
     * "Provavelmente" é o melhor que dá para saber sem uma contagem total — e contar 4.500 linhas
     * a cada abertura de tela custaria mais do que o botão a mais que às vezes aparece e não traz
     * nada.
     */
    val temMais: Boolean = false,
    val carregando: Boolean = true,
    val ocupado: Boolean = false,
    val erro: AppError? = null,
    val saiu: Boolean = false,
) {
    val souAdmin: Boolean get() = grupo?.myRole == MemberRole.ADMIN

    /**
     * Sou admin E estou sozinho — sair aqui APAGA o desafio (2.5-A).
     *
     * Vem de `memberCount`, do grupo, e não de `membros.size`: a lista pode ter falhado enquanto
     * o grupo carregou, e nesse caso `size` seria 0 e o diálogo ofereceria "Excluir" a quem tem
     * cinquenta pessoas no desafio. A contagem e a lista têm origens diferentes; quem manda numa
     * decisão irreversível é a que o servidor apurou.
     */
    val souUltimo: Boolean get() = souAdmin && grupo?.memberCount == 1
}

/**
 * Detalhe do grupo e gerência de membros (fatia A.4).
 *
 * **Gerar/revogar link de convite ficou de fora de propósito.** As rotas existem no servidor
 * desde a A.2 e estão testadas, mas link só serve com deep link, e deep link só funciona de
 * verdade com domínio https + App Links — as mensagerias não linkificam esquema próprio. Sem
 * domínio definido, o botão entregaria um UUID que ninguém consegue usar. O convite hoje
 * compartilha o CÓDIGO, que funciona em qualquer aplicativo. Ver DEBITOS.
 *
 * Vai à rede a cada abertura, sem cache: o **estado** do grupo é derivado do relógio do servidor,
 * e é dele que dependem as ações desta tela. Um `AGENDADO` velho ofereceria "convidar" para um
 * grupo que já começou — o servidor recusaria, e a culpa pareceria do app.
 */
class GrupoDetalheViewModel(
    private val groups: Groups,
    private val checkIns: CheckIns,
) : ViewModel() {

    private val _state = MutableStateFlow(GrupoDetalheState())
    val state: StateFlow<GrupoDetalheState> = _state.asStateFlow()

    private var enquete: Job? = null

    /**
     * Qual aba está na frente. Existe para o polling **atualizar só o que está visível**.
     *
     * Sem isso, o laço de 10s buscaria feed E ranking o tempo todo — o dobro de requisições, para
     * uma delas que ninguém está olhando.
     */
    private var abaVisivel: Aba = Aba.RANKING

    enum class Aba { SOBRE, RANKING, POSTS, MEMBROS }

    fun aoTrocarDeAba(aba: Aba, groupId: String) {
        if (aba == abaVisivel) return
        abaVisivel = aba
        // Atualiza na hora ao chegar: esperar até 10s para ver dado fresco numa aba que a pessoa
        // acabou de abrir seria o mesmo que não ter polling.
        atualizarAbaVisivel(groupId)
    }

    /**
     * POLLING de ~10s enquanto a tela está aberta (8.3, 10.2).
     *
     * Polling e não push: notificação é a fatia F, e mesmo lá a decisão foi **rejeitar** sync
     * silenciosa por push. Com a tela aberta, 10 segundos é o intervalo em que "o check-in do
     * amigo apareceu sozinho" ainda parece imediato.
     *
     * Amarrado ao ciclo de vida pela TELA (`ON_START`/`ON_STOP`): um laço que sobrevive à tela em
     * segundo plano é bateria e requisição que ninguém pediu.
     */
    fun iniciarEnquete(groupId: String) {
        if (enquete?.isActive == true) return
        enquete = viewModelScope.launch {
            while (isActive) {
                delay(INTERVALO_DO_FEED)
                atualizarAbaVisivel(groupId)
            }
        }
    }

    /**
     * Só a aba da frente.
     *
     * `SOBRE` e `MEMBROS` ficam de fora porque não mudam sozinhas: as especificações são imutáveis
     * com o grupo `ATIVO` (2-B.3), e a lista de membros só muda por ação do admin — que já
     * recarrega ao agir.
     */
    private fun atualizarAbaVisivel(groupId: String) {
        when (abaVisivel) {
            Aba.RANKING -> carregarRanking(groupId)
            Aba.POSTS -> carregarFeed(groupId)
            Aba.SOBRE, Aba.MEMBROS -> Unit
        }
    }

    /**
     * Recarrega o ranking. Falha em SILÊNCIO, como o feed: roda a cada 10 segundos, e um tropeço
     * de rede virando erro vermelho faria a tela piscar sozinha enquanto a pessoa lê.
     */
    fun carregarRanking(groupId: String) {
        viewModelScope.launch {
            val r = checkIns.ranking(groupId)
            _state.update { atual ->
                when (r) {
                    is AppResult.Success -> atual.copy(ranking = r.value, carregandoRanking = false)
                    is AppResult.Failure -> atual.copy(carregandoRanking = false)
                }
            }
        }
    }

    fun pararEnquete() {
        enquete?.cancel()
        enquete = null
    }

    /**
     * Recarrega o feed sem mexer no resto da tela.
     *
     * **Falha em silêncio de propósito.** Este método roda a cada 10 segundos; transformar um
     * tropeço de rede em erro vermelho faria a tela piscar sozinha enquanto a pessoa lê. A lista
     * conhecida continua valendo — a mesma escolha do `sincronizar` dos grupos.
     */
    fun carregarFeed(groupId: String) {
        viewModelScope.launch {
            val r = checkIns.feed(groupId)
            _state.update { atual ->
                when (r) {
                    is AppResult.Success -> atual.copy(
                        feed = juntar(novos = r.value, jaCarregados = atual.feed),
                        carregandoFeed = false,
                        temMais = r.value.size >= PAGINA,
                    )
                    is AppResult.Failure -> atual.copy(carregandoFeed = false)
                }
            }
        }
    }

    /**
     * Próxima página, usando o item mais antigo da lista como CURSOR.
     *
     * Cursor e não deslocamento: com item novo chegando por cima a cada 10s, `OFFSET 30` faria a
     * segunda página repetir ou pular linhas conforme a lista cresce por cima.
     */
    fun carregarMais(groupId: String) {
        val atual = _state.value
        if (atual.carregandoMais || !atual.temMais) return
        val cursor = atual.feed.lastOrNull()?.createdAt ?: return

        _state.update { it.copy(carregandoMais = true) }
        viewModelScope.launch {
            val r = checkIns.feed(groupId, antesDe = cursor)
            _state.update { estado ->
                when (r) {
                    is AppResult.Success -> estado.copy(
                        feed = estado.feed + r.value,
                        carregandoMais = false,
                        temMais = r.value.size >= PAGINA,
                    )
                    is AppResult.Failure -> estado.copy(carregandoMais = false, erro = r.error)
                }
            }
        }
    }

    /**
     * Junta a página recém-buscada com o que o usuário já paginou.
     *
     * **Sem isto, o polling apagaria o trabalho dele a cada dez segundos.** Ele carrega três
     * páginas, o laço de 10s busca a primeira, e a lista voltaria para 30 itens sozinha.
     *
     * A regra: a página fresca é AUTORIDADE sobre a janela de tempo que ela cobre — do item mais
     * antigo dela até agora. O que já estava carregado e é mais antigo que isso permanece. Assim
     * um check-in apagado pelo dono some de verdade (estava na janela e não voltou), e as páginas
     * antigas não são perdidas.
     *
     * Página fresca vazia significa grupo sem check-in nenhum — ela é sempre a mais recente.
     */
    private fun juntar(novos: List<CheckInDto>, jaCarregados: List<CheckInDto>): List<CheckInDto> {
        val corte = novos.lastOrNull()?.createdAt ?: return emptyList()
        return novos + jaCarregados.filter { it.createdAt < corte }
    }

    /**
     * Apagar o próprio check-in (4.11).
     *
     * Recarrega o GRUPO inteiro, não só o feed: apagar libera o slot do dia, e quem sabe disso é
     * o `myCheckInToday` do grupo — é ele que faz o botão de check-in voltar. Recarregar só o
     * feed tiraria o item da lista e deixaria a pessoa sem o botão, sem entender por quê.
     */
    fun apagarCheckIn(groupId: String, checkInId: String) = agir(groupId, recarrega = true) {
        val erro = (checkIns.apagar(groupId, checkInId) as? AppResult.Failure)?.error
        // O ranking muda junto: um check-in a menos pode custar uma posição. Recarregar só o feed
        // deixaria a pessoa vendo a posição antiga na aba do lado.
        if (erro == null) carregarRanking(groupId)
        erro
    }

    /**
     * Recarrega grupo + membros + feed.
     *
     * O esqueleto só aparece quando **ainda não há grupo**. Esta função também roda toda vez que a
     * tela volta ao foco — voltando do check-in, por exemplo — e ligar `carregando` ali faria a
     * tela inteira piscar em cima de um conteúdo que já estava correto.
     */
    fun carregar(groupId: String) {
        _state.update { it.copy(carregando = it.grupo == null, erro = null) }
        viewModelScope.launch {
            when (val g = groups.porId(groupId)) {
                is AppResult.Failure ->
                    _state.update { it.copy(carregando = false, erro = g.error) }
                is AppResult.Success -> {
                    // A falha da lista de membros PRECISA aparecer. A primeira versão fazia
                    // `as? AppResult.Success ?: emptyList()` e engolia o erro: a tela mostrava
                    // "PARTICIPANTES · 1" com nenhum participante embaixo, sem explicação
                    // nenhuma — e o defeito só apareceu porque eu reparei na contradição entre
                    // a contagem e a lista. Erro engolido é o mais caro de achar.
                    val m = groups.membros(groupId)
                    _state.update {
                        it.copy(
                            grupo = g.value,
                            membros = (m as? AppResult.Success)?.value.orEmpty(),
                            erro = (m as? AppResult.Failure)?.error,
                            carregando = false,
                        )
                    }
                    // As três abas carregam de uma vez na abertura: o ranking é a primeira coisa
                    // que se vê, e o feed logo atrás. Depois disso, só a aba visível se atualiza.
                    carregarFeed(groupId)
                    carregarRanking(groupId)
                }
            }
        }
    }

    override fun onCleared() {
        pararEnquete()
        super.onCleared()
    }

    fun expulsar(groupId: String, userId: String) = agir(groupId, recarrega = true) {
        (groups.expulsar(groupId, userId) as? AppResult.Failure)?.error
    }

    fun transferirAdmin(groupId: String, userId: String) = agir(groupId, recarrega = true) {
        (groups.transferirAdmin(groupId, userId) as? AppResult.Failure)?.error
    }

    fun sair(groupId: String) = agir(groupId) {
        when (val r = groups.sair(groupId)) {
            is AppResult.Success -> { _state.update { it.copy(saiu = true) }; null }
            is AppResult.Failure -> r.error
        }
    }

    /**
     * Um caminho só para toda ação: marca ocupado, executa, e recarrega quando a ação mudou a
     * composição do grupo. Sem isto, cada botão repetiria o mesmo cerimonial e um deles
     * esqueceria de recarregar — e a tela mostraria alguém que acabou de ser expulso.
     */
    private fun agir(groupId: String, recarrega: Boolean = false, bloco: suspend () -> AppError?) {
        // `update` e não leitura-e-escrita: o polling do feed escreve no MESMO estado a partir de
        // outra corrotina, e `bloco()` suspende no meio. É exatamente a corrida que fez a lista de
        // grupos sumir na fatia A.4.
        _state.update { it.copy(ocupado = true, erro = null) }
        viewModelScope.launch {
            val erro = bloco()
            _state.update { it.copy(ocupado = false, erro = erro) }
            if (erro == null && recarrega) carregar(groupId)
        }
    }

    private companion object {
        val INTERVALO_DO_FEED = 10.seconds

        /** Espelha o `PAGINA_PADRAO` do `CheckInService`. Se divergirem, o "tem mais" mente. */
        const val PAGINA = 30
    }
}
