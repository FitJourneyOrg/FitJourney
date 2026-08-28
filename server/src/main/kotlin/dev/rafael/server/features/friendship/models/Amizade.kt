package dev.rafael.server.features.friendship.models

import dev.rafael.server.features.friendship.services.FriendshipPolicy
import kotlinx.datetime.LocalDateTime
import kotlin.uuid.Uuid

/**
 * A relação entre duas pessoas, como o servidor a conhece (ARCH #35).
 *
 * Guarda o par em ordem CANÔNICA — `userA` é o menor uuid — porque é assim que ele vive no banco.
 * Quem quiser "o outro lado" usa [outroLado]; nenhum chamador deve comparar ids na mão.
 */
data class Amizade(
    val userA: Uuid,
    val userB: Uuid,
    val requestedBy: Uuid,
    val status: FriendshipPolicy.Estado,
    val createdAt: LocalDateTime,
    val respondedAt: LocalDateTime?,
) {
    /** O outro participante, visto por [eu]. */
    fun outroLado(eu: Uuid): Uuid = if (eu == userA) userB else userA

    /** Eu mandei este pedido? Decide entre "Aceitar/Recusar" e "Cancelar" na tela. */
    fun mandeiEu(eu: Uuid): Boolean = requestedBy == eu
}

/**
 * Uma pessoa numa lista social: id e nome, nada mais.
 *
 * **Sem nível, XP ou conquistas de propósito.** Lista de amigos não é ranking — pendurar número
 * nela criaria um placar paralelo ao do grupo, contra a [REGRA] do #18, e a mesma razão pela qual
 * XP ficou fora das superfícies de grupo vale aqui. Quem quiser o número abre o perfil.
 */
data class Pessoa(
    val userId: Uuid,
    val displayName: String,
)

/** Um pedido que chegou para mim, com quando chegou — a tela ordena por isso. */
data class PedidoRecebido(
    val de: Pessoa,
    val createdAt: LocalDateTime,
)
