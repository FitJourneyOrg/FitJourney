package dev.rafael.app.push

import android.content.Intent

/**
 * O que a notificação tocada pediu para abrir (F.1, ampliado na fatia F).
 *
 * ## Por que deixou de ser uma `String`
 *
 * Na F.1 havia um tipo só (`PEDIDO_DE_AMIZADE`) e o destino era a tela de Amigos — o `tipo`
 * sozinho bastava. Os tipos da fatia F apontam para **um grupo específico** e, no caso do
 * comentário, para **um check-in específico**: "alguém comentou" que abre a lista de grupos faz a
 * pessoa procurar o card, e ela abriu a notificação justamente para não procurar.
 *
 * Os ids já viajam no `data` do push desde a F.1 (o `fromUserId` abriu o precedente) e o
 * `FitJourneyMessagingService` já os repassa **todos** como extras. O que faltava era este lado
 * ler mais de um.
 *
 * ## Continua sendo dado burro
 *
 * Sem rota, sem `AppRoute`, sem navegação. Quem traduz isto em destino é o `AppNavHost`, que é
 * quem conhece o grafo — o serviço de push não conhece rotas e não deveria.
 */
data class DestinoDePush(
    val tipo: String,
    val groupId: String? = null,
    val checkInId: String? = null,
) {
    companion object {
        /**
         * Lê os extras. `null` quando o intent não veio de notificação — abrir o app pelo ícone
         * passa por aqui também.
         *
         * O `tipo` é obrigatório e os ids não: um tipo desconhecido, vindo de uma versão mais nova
         * do servidor, ainda tem destino (a central). **Melhor um destino genérico que funciona do
         * que nenhum** — a regra que a F.1 já tinha.
         */
        fun de(intent: Intent?): DestinoDePush? {
            val tipo = intent?.getStringExtra("tipo") ?: return null
            return DestinoDePush(
                tipo = tipo,
                groupId = intent.getStringExtra("groupId"),
                checkInId = intent.getStringExtra("checkInId"),
            )
        }
    }
}
