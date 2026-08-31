package dev.rafael.app.data.notificacoes

import dev.rafael.core.result.AppResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * O número do badge do ícone de notificações (F.1).
 *
 * ## Singleton, e não estado de ViewModel
 *
 * O ícone vive na `TopAppBar` do `AppNavHost` — acima de qualquer tela — e a central é outra
 * tela. Se cada uma tivesse o próprio ViewModel, marcar como lidas na central não apagaria o
 * badge da barra até alguém recarregar.
 *
 * Um `StateFlow` compartilhado resolve: quem marca como lida zera aqui, e o ícone reage.
 *
 * ## O número vem da MESMA lista da tela
 *
 * `count { readAt == null }`, e não uma rota `/count`. Duas fontes da mesma verdade divergem no
 * dia em que uma ganhar cache e a outra não — é a terceira vez que esta decisão aparece no
 * projeto (badge de pedidos, contador de membros, agora este).
 */
class ContadorDeNaoLidas(private val notificacoes: Notificacoes) {

    private val _quantidade = MutableStateFlow(0)
    val quantidade: StateFlow<Int> = _quantidade.asStateFlow()

    /** Chamado no boot e no ON_START de tela-raiz. Sem polling: pedido não é feed. */
    suspend fun atualizar() {
        when (val r = notificacoes.listar()) {
            is AppResult.Success -> _quantidade.value = r.value.count { it.readAt == null }
            // Falhar NÃO zera o badge. Zerar diria "você não tem notificações" quando a verdade é
            // "não consegui perguntar" — o mesmo erro que a C.1 evitou ao não apagar o perfil.
            is AppResult.Failure -> Unit
        }
    }

    /** A central acabou de marcar tudo como lido. Zera na hora, sem esperar uma requisição. */
    fun zerar() {
        _quantidade.value = 0
    }
}
