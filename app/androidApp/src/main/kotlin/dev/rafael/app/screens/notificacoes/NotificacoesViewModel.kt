package dev.rafael.app.screens.notificacoes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.rafael.app.data.notificacoes.ContadorDeNaoLidas
import dev.rafael.app.data.notificacoes.Notificacoes
import dev.rafael.contract.notificacao.NotificacaoDto
import dev.rafael.core.result.AppError
import dev.rafael.core.result.AppResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class NotificacoesState(
    val itens: List<NotificacaoDto> = emptyList(),
    val carregando: Boolean = true,
    /**
     * Puxar-para-atualizar em curso (F.2). SEPARADO de [carregando]: reusar o mesmo campo faria o
     * indicador do pull aparecer junto com o spinner da primeira carga.
     */
    val atualizando: Boolean = false,
    val erro: AppError? = null,
    /** Falha de um PULL (F.2): vai para um snackbar, não para o [erro] inline. Consumido uma vez. */
    val erroDoPull: AppError? = null,
)

/**
 * A central de notificações (F.1).
 *
 * ## Abrir marca tudo como lido
 *
 * Abrir a central é o gesto de "vi tudo isto". Exigir um toque por item deixaria o badge aceso
 * sobre coisas que a pessoa acabou de ler — e ninguém marca notificação uma a uma.
 *
 * **A lista continua mostrando o que era não-lido** com destaque visual, mesmo depois de marcar.
 * Se os itens perdessem o destaque na hora, a pessoa não saberia o que era novo — e o motivo de
 * ela ter aberto a tela era exatamente esse.
 */
class NotificacoesViewModel(
    private val notificacoes: Notificacoes,
    private val contador: ContadorDeNaoLidas,
) : ViewModel() {

    private val _state = MutableStateFlow(NotificacoesState())
    val state: StateFlow<NotificacoesState> = _state.asStateFlow()

    /** Entrada na tela: liga o spinner de carga. */
    fun carregar() = sincronizar(puxando = false)

    /** Puxar-para-atualizar: liga só [NotificacoesState.atualizando]; um pull por vez. */
    fun atualizar() = sincronizar(puxando = true)

    fun consumirErroDoPull() = _state.update { it.copy(erroDoPull = null) }

    private fun sincronizar(puxando: Boolean) {
        if (puxando && _state.value.atualizando) return
        viewModelScope.launch {
            _state.update { if (puxando) it.copy(atualizando = true) else it.copy(carregando = true) }

            when (val r = notificacoes.listar()) {
                is AppResult.Success -> {
                    _state.update { it.copy(itens = r.value, carregando = false, atualizando = false, erro = null) }

                    // Marca DEPOIS de ter a lista na mão: o snapshot que a tela mostra preserva o
                    // `readAt = null` dos itens novos, e é ele que desenha o destaque.
                    if (r.value.any { it.readAt == null }) {
                        notificacoes.marcarComoLidas()
                        contador.zerar()
                    }
                }
                // Preserva o que já estava na tela — falhar ao recarregar não apaga conteúdo.
                is AppResult.Failure -> _state.update {
                    it.copy(
                        carregando = false,
                        atualizando = false,
                        erro = if (puxando) it.erro else r.error,
                        erroDoPull = if (puxando) r.error else it.erroDoPull,
                    )
                }
            }
        }
    }
}
