package dev.rafael.app.screens.amigos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.rafael.app.data.amizades.Amizades
import dev.rafael.app.data.me.Me
import dev.rafael.contract.friendship.FriendRequestDto
import dev.rafael.contract.friendship.PersonDto
import dev.rafael.core.result.AppError
import dev.rafael.core.result.AppResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AmigosState(
    val meuCodigo: String = "",
    val amigos: List<PersonDto> = emptyList(),
    val pedidos: List<FriendRequestDto> = emptyList(),
    val carregando: Boolean = true,
    /** Puxar-para-atualizar em curso (F.2). SEPARADO de [carregando] pela mesma razão das Notificações. */
    val atualizando: Boolean = false,
    val ocupado: Boolean = false,
    val erro: AppError? = null,

    /**
     * Falha de um PULL (F.2). Vai para um snackbar, não para [erro]: quem puxou fez um gesto e
     * espera uma resposta efêmera, e a entrada na tela offline com dado local continua silenciosa
     * (ARCH #31). Consumido uma vez por [AmigosViewModel.consumirErroDoPull].
     */
    val erroDoPull: AppError? = null,

    /** O perfil achado pelo código, que a tela usa para navegar. Consumido uma vez. */
    val achado: String? = null,
    val buscando: Boolean = false,
    val erroDaBusca: AppError? = null,

    /**
     * A lista de pedidos que um PUSH trouxe e que ainda NÃO está na tela (B5). `null` = nada
     * esperando. A lista visível não muda sozinha: ver [AmigosViewModel.aoChegarPush].
     */
    val aguardando: List<FriendRequestDto>? = null,
) {
    /**
     * O contador é o `size`, não uma rota de contagem — duas fontes da mesma verdade divergem.
     *
     * Conta também o que está AGUARDANDO: o selo da aba diz a verdade na hora, mesmo enquanto a
     * lista espera o toque da pessoa para mostrar as linhas novas.
     */
    val pendentes: Int get() = (aguardando ?: pedidos).size

    /** Quantos pedidos chegaram e ainda não estão na lista visível. É o número do aviso. */
    val pedidosNovos: Int get() = aguardando?.let { pedidosNovos(pedidos, it) } ?: 0
}

/**
 * Quantos de [chegados] não aparecem em [visiveis], comparando pelo remetente.
 *
 * Só conta o que ENTROU. Um pedido que sumiu (o outro cancelou) fica na tela até a próxima
 * recarga: o servidor valida o "Aceitar" de qualquer jeito, e tirar uma linha de baixo do dedo é
 * exatamente o defeito que esta função existe para evitar.
 */
internal fun pedidosNovos(visiveis: List<FriendRequestDto>, chegados: List<FriendRequestDto>): Int =
    chegados.count { c -> visiveis.none { it.from.userId == c.from.userId } }

/**
 * Amigos e pedidos (ARCH #35).
 *
 * ## Uma requisição por ação, e recarrega tudo depois
 *
 * Sem escrita otimista, diferente de quase todo o resto do app (#30). Aceitar um pedido que o
 * servidor vai recusar — porque o outro cancelou, ou porque o teto de 500 estourou — mostraria
 * "vocês são amigos" e desfaria sozinho um segundo depois.
 *
 * **Aqui dado velho vira AÇÃO errada**, e não só tela velha: uma lista de pedidos desatualizada
 * faz a pessoa tocar "Aceitar" num pedido que já não existe.
 */
class AmigosViewModel(
    private val amizades: Amizades,
    private val me: Me,
) : ViewModel() {

    private val _state = MutableStateFlow(AmigosState())
    val state: StateFlow<AmigosState> = _state.asStateFlow()

    init {
        // O código vem do Flow do `/me`, que é cache-first e a ÚNICA coisa desta tela que pode
        // ser servida do local sem mentir — ele não muda por ação de terceiros.
        viewModelScope.launch {
            me.observar().collect { usuario ->
                _state.update { it.copy(meuCodigo = usuario?.code.orEmpty()) }
            }
        }
    }

    /** Entrada na tela e depois de cada ação: liga o spinner de carga. */
    fun carregar() = sincronizar(puxando = false)

    /**
     * Puxar-para-atualizar (F.2). É um GESTO da pessoa, então trocar a lista é permitido: o defeito
     * que o `aguardando` evita (linha que se move debaixo do dedo) vem de atualização que a pessoa
     * NÃO pediu. Um pull por vez.
     */
    fun atualizar() = sincronizar(puxando = true)

    private fun sincronizar(puxando: Boolean) {
        if (puxando && _state.value.atualizando) return
        viewModelScope.launch {
            _state.update { if (puxando) it.copy(atualizando = true) else it.copy(carregando = true) }

            me.sincronizar()

            val a = amizades.amigos()
            val p = amizades.pedidosRecebidos()

            val falha = (a as? AppResult.Failure)?.error ?: (p as? AppResult.Failure)?.error

            _state.update { s ->
                s.copy(
                    amigos = (a as? AppResult.Success)?.value ?: s.amigos,
                    pedidos = (p as? AppResult.Success)?.value ?: s.pedidos,
                    // Recarga completa (entrada na tela, ou depois de uma ação) mostra a verdade
                    // inteira: o que estava aguardando passa a estar na lista.
                    aguardando = null,
                    carregando = false,
                    atualizando = false,
                    // O primeiro erro que aparecer. As duas listas falham juntas na prática (é a
                    // mesma conexão), e mostrar dois avisos do mesmo problema é ruído.
                    erro = if (puxando) s.erro else falha,
                    erroDoPull = if (puxando) falha else s.erroDoPull,
                )
            }
        }
    }

    /**
     * Um push chegou com a tela aberta (B5).
     *
     * Só `PEDIDO_DE_AMIZADE` interessa: antes, QUALQUER push (comentário, conquista, denúncia...)
     * recarregava amigos, pedidos e `/me` inteiros e ligava o `carregando`.
     *
     * ## Por que não recarregar a lista visível
     *
     * Um pedido novo entra no meio das linhas e desloca os botões no instante em que a pessoa
     * ia tocar: "toquei em Aceitar e aceitei o pedido errado". Aqui dado velho vira AÇÃO errada
     * (ver o KDoc da classe), então a lista que a pessoa está olhando NÃO se move. O que chegou
     * vai para [AmigosState.aguardando] e a tela oferece "N pedido(s) novo(s)"; a lista só troca
     * quando a pessoa toca ([mostrarPedidosNovos]).
     *
     * Exceção: lista vazia. Sem linha, não há botão para ser deslocado, e o pedido aparece direto.
     * Falhar aqui é silencioso: o `ON_START` e o contador da barra cobrem o resto, e um aviso de
     * erro por um push seria barulho.
     */
    fun aoChegarPush(tipo: String) {
        if (tipo != TIPO_PEDIDO_DE_AMIZADE) return
        viewModelScope.launch {
            val chegados = (amizades.pedidosRecebidos() as? AppResult.Success)?.value ?: return@launch
            _state.update { s ->
                when {
                    s.pedidos.isEmpty() -> s.copy(pedidos = chegados, aguardando = null)
                    pedidosNovos(s.pedidos, chegados) > 0 -> s.copy(aguardando = chegados)
                    else -> s
                }
            }
        }
    }

    fun consumirErroDoPull() = _state.update { it.copy(erroDoPull = null) }

    /** A pessoa tocou no aviso: agora sim a lista troca. */
    fun mostrarPedidosNovos() = _state.update { s ->
        s.aguardando?.let { s.copy(pedidos = it, aguardando = null) } ?: s
    }

    fun aceitar(userId: String) = agir { amizades.aceitar(userId) }
    fun recusar(userId: String) = agir { amizades.recusar(userId) }
    fun remover(userId: String) = agir { amizades.remover(userId) }

    /**
     * Executa e RECARREGA. Não mexe na lista local na mão.
     *
     * Remover o item do estado seria escrita otimista pela porta dos fundos: se o servidor
     * recusasse, a pessoa veria o item sumir e voltar. Recarregar custa uma requisição e sempre
     * mostra a verdade.
     */
    private fun agir(acao: suspend () -> AppResult<Unit>) {
        viewModelScope.launch {
            _state.update { it.copy(ocupado = true, erro = null) }
            when (val r = acao()) {
                is AppResult.Success -> {
                    _state.update { it.copy(ocupado = false) }
                    carregar()
                }
                is AppResult.Failure -> _state.update { it.copy(ocupado = false, erro = r.error) }
            }
        }
    }

    /**
     * Busca pelo código. **Abre o PERFIL, não manda pedido** ([REGRA] #35).
     *
     * Sem isso, um erro de digitação viraria pedido de amizade a um desconhecido. Com o perfil
     * público, a tela de confirmação que o ADR previa é o próprio perfil — sai de graça.
     */
    fun buscarPorCodigo(codigo: String) {
        if (codigo.isBlank()) return
        viewModelScope.launch {
            _state.update { it.copy(buscando = true, erroDaBusca = null, achado = null) }
            when (val r = amizades.porCodigo(codigo)) {
                is AppResult.Success ->
                    _state.update { it.copy(buscando = false, achado = r.value.userId) }
                is AppResult.Failure ->
                    _state.update { it.copy(buscando = false, erroDaBusca = r.error) }
            }
        }
    }

    /** A tela chama depois de navegar, para o efeito não disparar de novo na volta da pilha. */
    fun buscaConsumida() = _state.update { it.copy(achado = null) }

    fun limparErroDaBusca() = _state.update { it.copy(erroDaBusca = null) }

    /**
     * Gera um código novo (35.5).
     *
     * Confirmação fica na TELA, não aqui: o ViewModel não pergunta, executa. Mas a tela precisa
     * perguntar mesmo — o código antigo morre na hora, e quem já passou o dele para alguém perde
     * o contato pendente.
     */
    fun regenerarCodigo() {
        viewModelScope.launch {
            _state.update { it.copy(ocupado = true, erro = null) }
            when (val r = amizades.regenerarMeuCodigo()) {
                is AppResult.Success -> {
                    _state.update { it.copy(ocupado = false, meuCodigo = r.value.code) }
                    // `forcar = true` porque o cache do `/me` guarda o código ANTIGO e não sabe
                    // que ele morreu. Sem isto, sair e voltar na tela mostraria o velho — e a
                    // pessoa passaria adiante um código que não resgata mais ninguém.
                    me.sincronizar(forcar = true)
                }
                is AppResult.Failure -> _state.update { it.copy(ocupado = false, erro = r.error) }
            }
        }
    }

    companion object {
        /** O `tipo` do push de pedido novo. Espelha `ChaveDeAviso.TIPO_PEDIDO_DE_AMIZADE`, no servidor. */
        const val TIPO_PEDIDO_DE_AMIZADE = "PEDIDO_DE_AMIZADE"
    }
}
