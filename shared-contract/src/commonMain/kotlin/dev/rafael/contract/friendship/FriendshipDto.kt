package dev.rafael.contract.friendship

import kotlinx.serialization.Serializable

/**
 * Uma pessoa numa lista social — amigos, pedidos, bloqueados (ARCH #35).
 *
 * **Só id e nome.** Sem nível, XP ou conquistas: pendurar número numa lista de amigos criaria um
 * placar paralelo ao do grupo, contra a [REGRA] do #18, e pela mesma razão que XP ficou fora das
 * superfícies de grupo. Quem quiser o número abre o perfil, que é um toque.
 *
 * **Sem o código da pessoa**, também de propósito. Publicá-lo em listas transformaria cada tela
 * social numa forma de colecionar códigos — e o código é justamente o que permite mandar pedido a
 * quem não te conhece. O seu vem no `/me`; o dos outros não vem em lugar nenhum.
 */
@Serializable
data class PersonDto(
    val userId: String,
    val displayName: String,
)

/** Um pedido que chegou para mim. `createdAt` é ISO e serve para ordenar. */
@Serializable
data class FriendRequestDto(
    val from: PersonDto,
    val createdAt: String,
)

/**
 * Como EU me relaciono com a pessoa deste perfil — o que decide o botão que a tela desenha.
 *
 * Resolvido no SERVIDOR, como o `myRole` do grupo e o `mine` do check-in. A tela não cruza
 * listas nem compara ids para descobrir se já mandou pedido.
 */
@Serializable
enum class FriendStatus {
    /** Nenhuma relação. Botão: **Adicionar**. */
    NENHUMA,

    /** Eu mandei e estou esperando. Botão: **Cancelar pedido**. */
    PEDIDO_ENVIADO,

    /** Ele mandou para mim. Botões: **Aceitar** / **Recusar**. */
    PEDIDO_RECEBIDO,

    /** Amigos. Botão: **Desfazer amizade**. */
    AMIGOS,

    /**
     * EU bloqueei esta pessoa. Botão: **Desbloquear**.
     *
     * Só aparece para quem bloqueou. Quem FOI bloqueado nunca vê este valor — recebe o perfil
     * indisponível (`available = false`), que é indistinguível de conta excluída.
     */
    BLOQUEADO_POR_MIM,
}
