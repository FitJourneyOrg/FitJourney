package dev.rafael.app.screens.comentarios

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.rafael.app.data.checkin.CheckIns
import dev.rafael.contract.checkin.CommentDto
import dev.rafael.core.result.AppError
import dev.rafael.core.result.AppResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ComentariosState(
    val itens: List<CommentDto> = emptyList(),
    val carregando: Boolean = true,

    /** O rascunho. Fica no state, e não num `remember` da tela, para sobreviver à rotação. */
    val rascunho: String = "",
    val enviando: Boolean = false,
    val erro: AppError? = null,

    /** O comentário cuja denúncia está aberta, ou `null` (fatia E.2). */
    val denunciando: CommentDto? = null,
    val denunciaEnviada: Boolean = false,
) {
    /**
     * Espelha a `SocialPolicy` do servidor. Duplicar a regra é deliberado, como no check-in: é o
     * que permite o botão nascer desabilitado em vez de a pessoa tocar e levar um 400.
     */
    val podeEnviar: Boolean get() = !enviando && rascunho.isNotBlank()

    val restantes: Int get() = MAX - rascunho.length

    companion object {
        /** 8.1. O servidor recusa acima disso, e o banco tem o mesmo `CHECK`. */
        const val MAX = 500
    }
}

/**
 * A conversa de um check-in (8.1, fatia E.1).
 *
 * ## Tela própria, e não expansão no card
 *
 * Decidido em 2026-09-06, depois de considerar as duas. Expandir inline economiza uma navegação e
 * cobra caro: o feed vira uma lista de alturas imprevisíveis que salta a cada polling de 10s, e o
 * teclado tapa metade do que a pessoa está lendo. Aqui a conversa tem a tela inteira e o campo
 * fica fixo embaixo.
 *
 * ## Sem cache, sem polling
 *
 * Como o feed (10.1). Comentário chega por ação de outra pessoa, e o `ON_START` cobre o caso de
 * voltar para a tela — polling aqui seria uma requisição a cada 10s para uma tela que quase sempre
 * tem zero comentário novo.
 */
class ComentariosViewModel(private val checkIns: CheckIns) : ViewModel() {

    private val _state = MutableStateFlow(ComentariosState())
    val state: StateFlow<ComentariosState> = _state.asStateFlow()

    fun carregar(groupId: String, checkInId: String) {
        viewModelScope.launch {
            _state.update { it.copy(carregando = true) }
            when (val r = checkIns.comentarios(groupId, checkInId)) {
                is AppResult.Success ->
                    _state.update { it.copy(itens = r.value, carregando = false, erro = null) }
                // Preserva o que já está na tela: falhar ao recarregar não apaga a conversa.
                is AppResult.Failure ->
                    _state.update { it.copy(carregando = false, erro = r.error) }
            }
        }
    }

    fun aoDigitar(texto: String) {
        // Corta no limite em vez de deixar digitar e recusar no envio. Descobrir que passou de 500
        // depois de escrever é perder o texto — e o servidor recusaria de qualquer forma.
        _state.update { it.copy(rascunho = texto.take(ComentariosState.MAX)) }
    }

    fun enviar(groupId: String, checkInId: String) {
        val texto = _state.value.rascunho.trim()
        if (texto.isEmpty() || _state.value.enviando) return

        _state.update { it.copy(enviando = true, erro = null) }
        viewModelScope.launch {
            when (val r = checkIns.comentar(groupId, checkInId, texto)) {
                is AppResult.Success -> _state.update {
                    // Ao FIM da lista: a conversa está em ordem cronológica, e o novo é o último.
                    // Acrescentar o que o SERVIDOR devolveu, e não o texto digitado, é o que
                    // garante que a tela mostre o que foi gravado — a lição do `display_name`.
                    it.copy(itens = it.itens + r.value, rascunho = "", enviando = false)
                }
                is AppResult.Failure ->
                    // O rascunho FICA: perder o texto por causa de uma queda de rede é o pior
                    // desfecho possível para quem acabou de escrever.
                    _state.update { it.copy(enviando = false, erro = r.error) }
            }
        }
    }

    /**
     * Apaga. Quem pode é o servidor que diz, pelo `canDelete` de cada item.
     *
     * Some da lista na hora e recarrega em caso de falha — o mesmo critério das reações: o gesto é
     * pequeno e desfazer é barato.
     */
    fun apagar(groupId: String, checkInId: String, comentarioId: String) {
        _state.update { it.copy(itens = it.itens.filterNot { c -> c.id == comentarioId }) }
        viewModelScope.launch {
            if (checkIns.apagarComentario(groupId, comentarioId) is AppResult.Failure) {
                carregar(groupId, checkInId)
            }
        }
    }

    // ---- denúncia de comentário (6.4, fatia E.2) ----

    /** Limpa o erro ao abrir: senão o diálogo nasceria mostrando a recusa do comentário anterior. */
    fun pedirDenuncia(alvo: CommentDto) =
        _state.update { it.copy(denunciando = alvo, erro = null) }

    fun cancelarDenuncia() = _state.update { it.copy(denunciando = null, erro = null) }

    /**
     * Envia a denúncia. **Sem otimismo e sem tirar o comentário da lista.**
     *
     * Ao contrário do apagar logo acima, aqui o item FICA: denunciar não remove nada, quem remove é
     * o admin depois de julgar. Sumir o comentário na hora diria à pessoa que ela tem um poder que
     * não tem — e o comentário reapareceria no próximo `ON_START`, o que é pior que nunca ter
     * sumido.
     */
    fun denunciar(groupId: String, motivo: String) {
        val alvo = _state.value.denunciando ?: return
        _state.update { it.copy(enviando = true, erro = null) }

        viewModelScope.launch {
            val r = checkIns.denunciarComentario(groupId, alvo.id, motivo)
            _state.update {
                it.copy(
                    enviando = false,
                    erro = (r as? AppResult.Failure)?.error,
                    // Fecha só no sucesso — o texto escrito sobrevive à falha de rede.
                    denunciando = if (r is AppResult.Success) null else it.denunciando,
                    denunciaEnviada = r is AppResult.Success,
                )
            }
        }
    }

    /**
     * Consome o aviso de "denúncia enviada".
     *
     * O `denunciaEnviada` é um evento disfarçado de estado, e sem este método ele ficaria `true`
     * para sempre — o aviso reapareceria a cada rotação de tela. Zerar ao mostrar é o mínimo que
     * evita isso sem trazer um canal de eventos para uma tela só.
     */
    fun avisoVisto() = _state.update { it.copy(denunciaEnviada = false) }

    fun limparErro() = _state.update { it.copy(erro = null) }
}
