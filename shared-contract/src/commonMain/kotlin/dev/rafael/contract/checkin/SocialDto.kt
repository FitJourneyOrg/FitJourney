package dev.rafael.contract.checkin

import kotlinx.serialization.Serializable

/**
 * Um comentário em check-in (8.1, fatia E.1).
 *
 * **Não existe campo de edição**, e a ausência é a regra: comentário não se edita. Um `editedAt`
 * aqui convidaria alguém a implementar a edição, e o que 49 pessoas leram deixaria de ser o que
 * está escrito.
 */
@Serializable
data class CommentDto(
    val id: String,
    val checkInId: String,
    val userId: String,
    /** Nome, nunca e-mail — o e-mail não atravessa a fronteira do grupo ([INV] #33). */
    val displayName: String,
    val body: String,
    /** ISO-8601, relógio do SERVIDOR. */
    val createdAt: String,
    /**
     * Posso apagar este comentário? Resolvido no servidor, como o `canDelete` do check-in.
     *
     * Verdadeiro para o **autor**, para o **dono do check-in** e para o **admin do grupo**
     * (2026-09-06, emendado em 09-07). A tela não compara ids nem consulta papel: ela desenha o que
     * o servidor já decidiu.
     */
    val canDelete: Boolean = false,

    /**
     * Dá para denunciar este comentário (6.4, fatia E.2)? Falso para o autor e fora dos 7 dias.
     *
     * Denunciar e apagar **não são o mesmo caminho**, e os dois podem aparecer no mesmo comentário
     * sem contradição: quem pode apagar resolve na hora; quem não pode, encaminha ao admin. Um
     * comentário do dono do check-in visto por ele mesmo tem `canDelete = true` e `canReport =
     * false` — não faz sentido pedir ao admin o que se pode fazer sozinho.
     */
    val canReport: Boolean = false,
)

/**
 * Quantas reações de cada emoji, e qual é a minha (8.2).
 *
 * O agrupamento vem PRONTO do servidor. A alternativa — mandar a lista crua de reações e deixar a
 * tela contar — faria cada aparelho baixar 50 linhas para exibir "12 👍", e o feed já é a tela mais
 * pesada do app.
 */
@Serializable
data class ReactionSummaryDto(
    val emoji: String,
    val count: Int,
    /** Fui eu que reagi com este emoji? É o que deixa o botão marcado. */
    val mine: Boolean = false,
)

/**
 * O que se manda ao comentar. O `checkInId` vai na rota, não aqui.
 *
 * Sem `id` do cliente, ao contrário do check-in (#30): comentário é **online-only** e não passa
 * pelo outbox — não há escrita offline para tornar idempotente.
 */
@Serializable
data class NovoComentarioRequest(val body: String)

/** O emoji escolhido. Vocabulário fechado no cliente, aberto no banco (ver V44). */
@Serializable
data class ReagirRequest(val emoji: String)
