package dev.rafael.app.screens.moderacao

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.rafael.app.data.checkin.CheckIns
import dev.rafael.contract.checkin.ReportItemDto
import dev.rafael.core.result.AppError
import dev.rafael.core.result.AppResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ModeracaoState(
    val itens: List<ReportItemDto> = emptyList(),
    val carregando: Boolean = true,
    val erro: AppError? = null,

    /** O caso cuja confirmação está aberta, e o que se decidiu nele. `null` = nenhum. */
    val confirmando: Julgamento? = null,

    /** O alvo sendo julgado agora, para desabilitar só o cartão dele. */
    val julgando: String? = null,
)

/**
 * Uma decisão à espera de confirmação.
 *
 * Guarda o `acatar` junto do alvo porque o diálogo diz coisas OPOSTAS nos dois casos — "isto tira
 * o ponto" contra "isto encerra o caso" — e um diálogo genérico ("confirmar?") faria o admin
 * confirmar sem saber o que.
 */
data class Julgamento(val item: ReportItemDto, val acatar: Boolean)

/**
 * A fila de moderação do admin (6.2, fatia E.2).
 *
 * ## Sem polling, ao contrário do feed
 *
 * O feed muda por ação de 49 pessoas o tempo todo; a fila muda quando alguém denuncia, o que é
 * raro. Um laço de 10 segundos aqui seria uma requisição por dezena de segundos numa tela que
 * quase sempre está igual — e a tela existe justamente para o admin agir, não para observar.
 *
 * `ON_START` cobre o caso de voltar de outra tela, e cada julgamento recarrega o que mudou.
 */
class ModeracaoViewModel(private val checkIns: CheckIns) : ViewModel() {

    private val _state = MutableStateFlow(ModeracaoState())
    val state: StateFlow<ModeracaoState> = _state.asStateFlow()

    fun carregar(groupId: String) {
        viewModelScope.launch {
            _state.update { it.copy(carregando = true) }
            when (val r = checkIns.fila(groupId)) {
                is AppResult.Success ->
                    _state.update { it.copy(itens = r.value, carregando = false, erro = null) }
                // Preserva o que já está na tela: falhar ao recarregar não apaga a fila conhecida.
                is AppResult.Failure ->
                    _state.update { it.copy(carregando = false, erro = r.error) }
            }
        }
    }

    fun pedirConfirmacao(item: ReportItemDto, acatar: Boolean) =
        _state.update { it.copy(confirmando = Julgamento(item, acatar)) }

    fun cancelarConfirmacao() = _state.update { it.copy(confirmando = null) }

    /**
     * Julga. **Sem otimismo e sem tirar o item da lista antes da resposta.**
     *
     * O critério é o mesmo da denúncia e o oposto da reação: dado velho só é perigoso quando vira
     * AÇÃO errada. Sumir o caso da fila e depois ele voltar faria o admin achar que já decidiu — e
     * **a decisão é irreversível e não tem recurso (6.7)**. Aqui o dobro de cuidado se paga.
     *
     * Recarrega a fila inteira em vez de remover o item localmente: acatar uma denúncia de
     * comentário o APAGA, e o CASCADE da V45 pode fechar outros casos junto. Só o servidor sabe o
     * que sobrou.
     */
    fun julgar(groupId: String) {
        val decisao = _state.value.confirmando ?: return
        val alvo = decisao.item.targetId

        _state.update { it.copy(confirmando = null, julgando = alvo, erro = null) }
        viewModelScope.launch {
            val r = checkIns.julgar(groupId, alvo, decisao.acatar)
            _state.update { it.copy(julgando = null, erro = (r as? AppResult.Failure)?.error) }
            if (r is AppResult.Success) carregar(groupId)
        }
    }

    fun limparErro() = _state.update { it.copy(erro = null) }
}
