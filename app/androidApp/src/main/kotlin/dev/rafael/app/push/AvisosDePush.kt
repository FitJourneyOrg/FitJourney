package dev.rafael.app.push

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * "Chegou um push AGORA" — para quem está com o app aberto (F.1).
 *
 * ## O buraco que isto fecha
 *
 * A tela de Amigos recarrega no `ON_START`; o badge da barra recarrega ao trocar de tela-raiz.
 * Os dois assumem que a pessoa **saiu e voltou** — e o push existe justamente para avisar quem
 * não saiu. Na bateria da F.1 o sintoma foi exato: a notificação chegava na bandeja e o badge de
 * Pedidos só subia depois de sair da tela e voltar.
 *
 * Polling resolveria e foi descartado no [dev.rafael.app.data.notificacoes.ContadorDeNaoLidas]
 * com razão: pedido de amizade não é feed. **O evento já existe — falta entregá-lo.**
 *
 * ## Por que um bus, e não o Service chamando o repositório
 *
 * O `FitJourneyMessagingService` roda fora de qualquer tela, e às vezes com o app inteiro morto.
 * Se ele buscasse dados, faria requisição para uma tela que não existe. O bus deixa a decisão de
 * recarregar com quem está vivo para usá-la: sem coletor, o evento simplesmente se perde — e não
 * se perde nada, porque o app vai recarregar ao abrir de qualquer forma.
 *
 * `extraBufferCapacity` + `tryEmit`: o emissor **nunca bloqueia**. É o mesmo contrato do
 * `SessionExpiryBus`, e pela mesma razão — quem emite está numa thread do sistema.
 */
class AvisosDePush {

    private val _eventos = MutableSharedFlow<String>(extraBufferCapacity = 8)

    /** O `tipo` da notificação (`PEDIDO_DE_AMIZADE`, …). Quem ouve decide se lhe interessa. */
    val eventos: SharedFlow<String> = _eventos.asSharedFlow()

    fun chegou(tipo: String) {
        _eventos.tryEmit(tipo)
    }
}
