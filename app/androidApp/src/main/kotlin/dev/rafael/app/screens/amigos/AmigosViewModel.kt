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
    val ocupado: Boolean = false,
    val erro: AppError? = null,

    /** O perfil achado pelo código, que a tela usa para navegar. Consumido uma vez. */
    val achado: String? = null,
    val buscando: Boolean = false,
    val erroDaBusca: AppError? = null,
) {
    /** O contador é o `size`, não uma rota de contagem — duas fontes da mesma verdade divergem. */
    val pendentes: Int get() = pedidos.size
}

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

    fun carregar() {
        viewModelScope.launch {
            _state.update { it.copy(carregando = true) }

            me.sincronizar()

            val a = amizades.amigos()
            val p = amizades.pedidosRecebidos()

            _state.update { s ->
                s.copy(
                    amigos = (a as? AppResult.Success)?.value ?: s.amigos,
                    pedidos = (p as? AppResult.Success)?.value ?: s.pedidos,
                    carregando = false,
                    // O primeiro erro que aparecer. As duas listas falham juntas na prática (é a
                    // mesma conexão), e mostrar dois avisos do mesmo problema é ruído.
                    erro = (a as? AppResult.Failure)?.error ?: (p as? AppResult.Failure)?.error,
                )
            }
        }
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
}
