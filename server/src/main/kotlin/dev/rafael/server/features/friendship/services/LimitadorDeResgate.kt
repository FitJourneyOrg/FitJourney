package dev.rafael.server.features.friendship.services

import dev.rafael.server.features.user.services.UserCodePolicy
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * A segunda das três defesas do código de usuário (35.5): **10 tentativas por hora, por conta**.
 *
 * ## Por que em memória, e não numa tabela
 *
 * O que este limitador protege é a VARREDURA do espaço de códigos, que só faz sentido em rajada.
 * Reiniciar o servidor zera as contagens — e isso é aceitável: quem está varrendo perde muito
 * mais tempo com o teto de 10/h do que ganha com um reinício que ele não controla.
 *
 * Uma tabela custaria uma escrita por tentativa, inclusive nas legítimas, para proteger contra um
 * atacante que precisaria de **anos** mesmo sem limite nenhum (32⁸ ≈ 1 trilhão). O limite existe
 * para deixar rastro e encarecer, não para ser a única barreira — a barreira é o tamanho do
 * código, e a saída de quem é importunado é regenerar.
 *
 * ## O que conta como tentativa
 *
 * Só código **bem formado** que não encontrou ninguém. Erro de digitação que nem passa pelo
 * `UserCodePolicy.normalizar` não consome cota — senão o usuário de dedo gordo seria punido pela
 * defesa contra o atacante. Acerto também não consome: quem encontrou não está varrendo.
 */
class LimitadorDeResgate(private val clock: Clock = Clock.System) {

    private val janela = 1.hours
    private val mutex = Mutex()

    /** uid → instantes das tentativas malsucedidas dentro da janela. */
    private val tentativas = mutableMapOf<Uuid, MutableList<Instant>>()

    /**
     * Registra uma tentativa FRUSTRADA e diz se a pessoa ainda pode tentar.
     *
     * A limpeza da janela acontece na leitura, e não num varredor agendado: sem cron, sem mais
     * uma peça falhando em silêncio. Quem não tenta mais nunca ocupa memória porque a lista só
     * cresce quando alguém erra.
     */
    suspend fun registrarFalhaEPermitir(userId: Uuid): Boolean = mutex.withLock {
        val agora = clock.now()
        val corte = agora - janela

        val lista = tentativas.getOrPut(userId) { mutableListOf() }
        lista.removeAll { it < corte }
        lista.add(agora)

        // A entrada some quando a janela esvazia — o mapa não vira vazamento de memória lento.
        if (lista.isEmpty()) tentativas.remove(userId)

        lista.size <= UserCodePolicy.TENTATIVAS_POR_HORA
    }

    /** Só consulta, sem registrar. Usado antes de ir ao banco. */
    suspend fun podeTentar(userId: Uuid): Boolean = mutex.withLock {
        val corte = clock.now() - janela
        val lista = tentativas[userId] ?: return true
        lista.removeAll { it < corte }
        lista.size < UserCodePolicy.TENTATIVAS_POR_HORA
    }
}
