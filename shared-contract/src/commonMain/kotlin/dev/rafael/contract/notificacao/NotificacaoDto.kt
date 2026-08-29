package dev.rafael.contract.notificacao

import kotlinx.serialization.Serializable

/**
 * Uma notificação na central (F.1).
 *
 * `title` e `body` vêm **já renderizados** do servidor — o cliente não monta frase. Assim a
 * notificação de ontem continua dizendo o que dizia ontem, mesmo que a pessoa citada troque de
 * nome depois.
 */
@Serializable
data class NotificacaoDto(
    val id: String,
    val type: String,
    val title: String,
    val body: String,
    /** Para a navegação: `tipo`, `fromUserId`. O mesmo `data` que o push carrega. */
    val data: Map<String, String> = emptyMap(),
    /** ISO, ou `null` se ainda não foi lida. É o que o contador do sininho conta. */
    val readAt: String? = null,
    val createdAt: String,
)
