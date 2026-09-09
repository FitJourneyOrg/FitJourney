package dev.rafael.app.screens.grupos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.rafael.app.data.checkin.CheckIns
import dev.rafael.app.data.groups.Groups
import dev.rafael.contract.checkin.CheckInDto
import dev.rafael.contract.checkin.ReactionSummaryDto
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

/**
 * O que está sendo denunciado, enquanto o diálogo está aberto (fatia E.2).
 *
 * Carrega o `titulo` já pronto em vez de o id sozinho: a tela precisa dizer O QUE se está
 * denunciando ("o check-in de Ana", "o comentário de João"), e buscar isso de novo na lista no
 * momento de desenhar exporia o diálogo ao polling — o item pode ter saído do feed.
 */
data class AlvoDeDenuncia(
    val id: String,
    val ehComentario: Boolean,
    val titulo: String,
)

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

    /**
     * Quantos casos esperam o admin (fatia E.2). Zero para membro comum — ele nem consulta.
     *
     * Fica no estado do detalhe, e não numa tela própria, porque é a BARRA que o mostra: o admin
     * precisa saber que há algo a julgar sem abrir a fila para descobrir.
     */
    val denunciasPendentes: Int = 0,

    /**
     * O alvo do diálogo de denúncia aberto, ou `null`.
     *
     * O diálogo é do ViewModel e não da tela porque o motivo é obrigatório e a chamada é
     * assíncrona: um `remember` local perderia o texto na primeira recomposição vinda do polling.
     */
    val denunciando: AlvoDeDenuncia? = null,
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
                    // Depois de `grupo` estar no estado: `carregarPendentes` consulta `souAdmin`,
                    // que vem do grupo. Chamado antes, sairia sem fazer nada — e o badge só
                    // apareceria na segunda abertura da tela.
                    carregarPendentes(groupId)
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
    // ---- reações (fatia E.1) ----
    //
    // Comentários NÃO ficam aqui: eles têm tela própria (`ComentariosScreen`), com ViewModel
    // próprio. Reação é do CARD — um toque que muda um número na hora —, comentário é uma
    // conversa que merece o espaço da tela e o campo de texto sem o teclado cobrindo o feed.

    /**
     * Põe, TROCA ou tira a reação (8.2).
     *
     * Tocar no emoji que já é o meu **tira** — é o gesto que todo app com reação tem, e sem ele
     * não haveria como desfazer sem escolher outro.
     *
     * **Escrita otimista aqui**, ao contrário do resto da fatia: reagir é o gesto mais barato do
     * feed, e esperar a rede para pintar o botão faria cada toque parecer travado. Se falhar, a
     * recarga do feed desfaz — e o custo de um emoji errado por dois segundos é nenhum. É o mesmo
     * critério do #30: **dado velho só é perigoso quando vira AÇÃO errada**, e aqui não vira.
     */
    fun reagir(groupId: String, checkInId: String, emoji: String) {
        val atual = _state.value.feed.firstOrNull { it.id == checkInId } ?: return
        val minhaAtual = atual.reactions.firstOrNull { it.mine }?.emoji
        val tirando = minhaAtual == emoji

        _state.update { s ->
            s.copy(feed = s.feed.map { if (it.id == checkInId) it.comReacao(emoji, tirando) else it })
        }

        viewModelScope.launch {
            val r = if (tirando) {
                checkIns.desreagir(groupId, checkInId)
            } else {
                checkIns.reagir(groupId, checkInId, emoji)
            }
            // Falhou: recarrega o feed e a verdade do servidor volta. Não mostro erro — o gesto é
            // pequeno demais para interromper a pessoa com um aviso.
            if (r is AppResult.Failure) carregarFeed(groupId)
        }
    }

    /** Recalcula as contagens localmente, do jeito que o servidor recalcularia. */
    private fun CheckInDto.comReacao(emoji: String, tirando: Boolean): CheckInDto {
        val semAMinha = reactions.mapNotNull { r ->
            if (!r.mine) r else (r.copy(count = r.count - 1, mine = false)).takeIf { it.count > 0 }
        }
        if (tirando) return copy(reactions = semAMinha)

        val jaTem = semAMinha.any { it.emoji == emoji }
        val comAMinha = if (jaTem) {
            semAMinha.map { if (it.emoji == emoji) it.copy(count = it.count + 1, mine = true) else it }
        } else {
            semAMinha + ReactionSummaryDto(emoji, 1, mine = true)
        }
        // Mesma ordem do servidor: sem isto, o botão dança de lugar entre a escrita otimista e a
        // resposta real.
        return copy(reactions = comAMinha.sortedWith(compareByDescending<ReactionSummaryDto> { it.count }.thenBy { it.emoji }))
    }

    // ---- denúncia (fatia E.2) ----

    /**
     * Abre o diálogo. Nada vai à rede aqui — o motivo é obrigatório e ainda não foi escrito.
     *
     * **Limpa o erro ao abrir.** Sem isso, o diálogo do próximo check-in nasceria mostrando a
     * recusa do anterior — e a pessoa leria "você já denunciou este check-in" sobre um que ela
     * nunca tocou.
     */
    fun pedirDenuncia(alvo: AlvoDeDenuncia) =
        _state.update { it.copy(denunciando = alvo, erro = null) }

    fun cancelarDenuncia() = _state.update { it.copy(denunciando = null, erro = null) }

    /**
     * Envia a denúncia. **Sem otimismo**, ao contrário da reação.
     *
     * A diferença é o critério do #30: dado velho só é perigoso quando vira AÇÃO errada. Um emoji
     * errado por dois segundos não faz nada; "sua denúncia foi enviada" quando ela não foi faz a
     * pessoa parar de esperar uma resposta que nunca virá. **Confirmação otimista de um ato que
     * depende de outra pessoa é mentira com prazo.**
     */
    fun denunciar(groupId: String, motivo: String) {
        val alvo = _state.value.denunciando ?: return
        _state.update { it.copy(ocupado = true, erro = null) }

        viewModelScope.launch {
            val r = if (alvo.ehComentario) {
                checkIns.denunciarComentario(groupId, alvo.id, motivo)
            } else {
                checkIns.denunciarCheckIn(groupId, alvo.id, motivo)
            }
            _state.update {
                it.copy(
                    ocupado = false,
                    erro = (r as? AppResult.Failure)?.error,
                    // O diálogo só fecha quando deu certo: fechar na falha apagaria o texto que a
                    // pessoa escreveu, e ela teria de redigir tudo de novo para tentar outra vez.
                    denunciando = if (r is AppResult.Success) null else it.denunciando,
                )
            }
            // O feed muda: o check-in denunciado passa a EM_ANALISE e perde o `canReport`.
            if (r is AppResult.Success && !alvo.ehComentario) carregarFeed(groupId)
        }
    }

    /**
     * O contador do badge. Só o admin chama — membro comum receberia 403.
     *
     * Falha em silêncio, como o feed: um badge que não subiu é menos ruim que um erro vermelho na
     * barra por causa de um tropeço de rede.
     */
    /**
     * 6.10: o admin invalida direto.
     *
     * Recarrega feed E ranking: o selo muda no card e o ponto sai da contagem. Recarregar só o
     * feed deixaria a posição antiga na aba do lado — o mesmo defeito que o `apagarCheckIn`
     * evitou.
     */
    fun invalidarDireto(groupId: String, checkInId: String) = agir(groupId) {
        val erro = (checkIns.invalidar(groupId, checkInId) as? AppResult.Failure)?.error
        if (erro == null) {
            carregarFeed(groupId)
            carregarRanking(groupId)
            // A invalidação direta FECHA as denúncias abertas daquele check-in: o badge tem de
            // descer junto, senão a fila prometeria um caso que não existe mais.
            carregarPendentes(groupId)
        }
        erro
    }

    fun carregarPendentes(groupId: String) {
        if (!_state.value.souAdmin) return
        viewModelScope.launch {
            val r = checkIns.pendentes(groupId)
            if (r is AppResult.Success) _state.update { it.copy(denunciasPendentes = r.value) }
        }
    }

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
