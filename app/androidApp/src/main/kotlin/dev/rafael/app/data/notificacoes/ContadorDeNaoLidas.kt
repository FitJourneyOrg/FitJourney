package dev.rafael.app.data.notificacoes

import dev.rafael.core.result.AppResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

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
 *
 * ## Uma pergunta por vez (B4)
 *
 * No boot, três gatilhos perguntam a MESMA coisa quase juntos: a troca de rota (Splash, Home), a
 * sessão restaurada (`ReagirASessao`) e, mais tarde, o push. Eram quatro `GET /me/notifications`
 * para uma única resposta. Agora é *single-flight*: quem chega com uma pergunta já em andamento
 * ESPERA por ela em vez de fazer outra.
 *
 * ⚠️ **O push é a exceção, e por isso não se usou uma janela de tempo.** A pergunta em andamento
 * pode ter saído ANTES de a notificação existir no servidor, e juntar-se a ela devolveria um
 * número velho no exato momento em que a pessoa mais olha para o badge. Então quem chega por
 * evento ([atualizar] com `aposEvento = true`) pede UMA repetição quando a corrente terminar.
 * Sem relógio: nada aqui depende do tempo, só de haver ou não uma pergunta em andamento.
 */
class ContadorDeNaoLidas(private val notificacoes: Notificacoes) {

    private val _quantidade = MutableStateFlow(0)
    val quantidade: StateFlow<Int> = _quantidade.asStateFlow()

    /** Guarda o estado abaixo. Seções curtas e sem suspender dentro: nunca segura a rede. */
    private val estado = Mutex()

    /** A pergunta em andamento, ou `null`. Quem chega e a encontra espera por ela. */
    private var emAndamento: CompletableDeferred<Unit>? = null

    /** Chegou um evento durante a pergunta: a resposta dela pode ser anterior a ele. */
    private var refazer = false

    /**
     * Chamado no boot, na troca de rota e na sessão. Sem polling: pedido não é feed.
     *
     * @param aposEvento `true` quando o gatilho é um push (o número mudou no servidor agora).
     *   Se já há pergunta em andamento, ela é refeita UMA vez ao terminar, em vez de reaproveitada.
     */
    suspend fun atualizar(aposEvento: Boolean = false) {
        val aguardar = estado.withLock {
            val atual = emAndamento
            if (atual != null) {
                if (aposEvento) refazer = true
                atual
            } else {
                emAndamento = CompletableDeferred()
                null
            }
        }
        if (aguardar != null) {
            aguardar.await()
            return
        }

        try {
            do {
                estado.withLock { refazer = false }
                perguntar()
            } while (estado.withLock { refazer })
        } finally {
            // Mesmo cancelado ou com erro: quem espera TEM de acordar, e a próxima chamada tem de
            // poder perguntar. Sem isto, uma tela fechada no meio da requisição travaria o badge.
            withContext(NonCancellable) {
                estado.withLock { emAndamento.also { emAndamento = null } }?.complete(Unit)
            }
        }
    }

    private suspend fun perguntar() {
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
