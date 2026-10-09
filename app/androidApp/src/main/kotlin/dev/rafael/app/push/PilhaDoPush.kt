package dev.rafael.app.push

import dev.rafael.app.navigation.AppRoute
import dev.rafael.app.screens.grupos.AbasDoGrupo

/**
 * As telas que o toque na notificação empilha, em ordem (a última é a que aparece).
 *
 * Função pura, fora do `AppNavHost`, para a tradução tipo → rota poder ser testada sem Compose.
 *
 * ## Por que o comentário empilha DUAS telas
 *
 * Com o app morto a pilha é Splash → Home → destino. Se o destino é a conversa de um check-in, o
 * voltar cai na Home e quem leu o comentário perde o card e o resto do feed. Por isso o
 * comentário empilha o grupo (aba de posts) antes da conversa.
 *
 * ## Por que [grupoAberto]
 *
 * Com o app ABERTO já dentro daquele grupo, a tela do grupo está na pilha: empilhar de novo a
 * duplicaria. Então, nesse caso, empilha só a conversa.
 *
 * O `?:` das rotas que dependem de id é intencional: o `data` do push não tem contrato de
 * compilação, e **cair na central é sempre melhor que abrir um grupo que não existe**.
 *
 * @param grupoAberto id do grupo cuja tela de detalhe está visível agora, ou `null`.
 */
internal fun pilhaDoPush(destino: DestinoDePush, grupoAberto: String?): List<AppRoute> {
    val g = destino.groupId
    return when (destino.tipo) {
        "PEDIDO_DE_AMIZADE" -> listOf(AppRoute.Amigos)

        "COMENTARIO_NO_CHECKIN" -> {
            val c = destino.checkInId
            when {
                g == null || c == null -> listOf(AppRoute.Notificacoes)
                g == grupoAberto -> listOf(AppRoute.Comentarios(g, c))
                else -> listOf(AppRoute.GrupoDetalhe(g, AbasDoGrupo.POSTS), AppRoute.Comentarios(g, c))
            }
        }

        // A fila é onde o admin AGE.
        "DENUNCIA_NO_GRUPO", "FILA_PARADA" ->
            listOf(g?.let { AppRoute.Moderacao(it) } ?: AppRoute.Notificacoes)

        // Em abas diferentes: o card denunciado/invalidado está em POSTS; a entrada no desafio,
        // em MEMBROS.
        "DENUNCIA_CONTRA_MIM", "CHECK_IN_INVALIDADO" ->
            listOf(g?.let { AppRoute.GrupoDetalhe(it, AbasDoGrupo.POSTS) } ?: AppRoute.Notificacoes)

        "ENTRADAS_DO_DIA" ->
            listOf(g?.let { AppRoute.GrupoDetalhe(it, AbasDoGrupo.MEMBROS) } ?: AppRoute.Notificacoes)

        // `achievementId` ausente não é erro (servidor antigo): a tela abre normal, sem diálogo.
        "CONQUISTA_DESBLOQUEADA" -> listOf(AppRoute.Conquistas(destaque = destino.achievementId))

        // Tipo de uma versão mais nova do servidor: a central sabe mostrar qualquer notificação.
        else -> listOf(AppRoute.Notificacoes)
    }
}
