package dev.rafael.server.features.checkin.models

import kotlinx.datetime.LocalDateTime
import kotlin.uuid.Uuid

/**
 * Um comentário como o servidor o lê (fatia E.1).
 *
 * Traz o `displayName` junto porque o feed sempre precisa dele e buscá-lo depois seria um N+1 —
 * mesma escolha do [CheckInComAutor]. **E-mail não existe aqui**: ele não atravessa a fronteira do
 * grupo ([INV] #33), e o jeito de garantir isso é não haver campo.
 */
data class Comentario(
    val id: Uuid,
    val checkInId: Uuid,
    val groupId: Uuid,
    val userId: Uuid,
    val displayName: String,
    val body: String,
    val createdAt: LocalDateTime,
)

/** O que se grava. O texto já vem tratado pela `SocialPolicy` — o repositório não normaliza. */
data class NovoComentario(
    val id: Uuid,
    val checkInId: Uuid,
    val groupId: Uuid,
    val userId: Uuid,
    val body: String,
    val createdAt: LocalDateTime,
)

/**
 * Quantas reações de um emoji num check-in, e se a minha está entre elas.
 *
 * O `souEu` sai do `GROUP BY` com um `bool_or`, não de uma segunda consulta: pedir "as reações" e
 * depois "a minha reação" seriam duas idas ao banco para desenhar um botão.
 */
data class ReacaoAgrupada(
    val emoji: String,
    val quantidade: Int,
    val souEu: Boolean,
)
