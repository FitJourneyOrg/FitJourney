package dev.rafael.server.features.checkin.services

import dev.rafael.contract.group.MemberRole

/**
 * As regras de comentário e reação, em Kotlin puro (fatia E.1).
 *
 * Mesma escolha do [CheckInPolicy] e do `GroupPolicy`: sem banco, sem HTTP, sem relógio próprio.
 */
object SocialPolicy {

    /** 8.1. O CHECK do banco usa o mesmo número — os dois têm de concordar. */
    const val MAX_COMENTARIO = 500

    /**
     * As seis reações (emenda à 8.2, decidida em 2026-09-06).
     *
     * ## A 8.2 dizia "qualquer emoji", e foi emendada
     *
     * O texto original permitia todo o Unicode. Na hora de desenhar a tela, três problemas
     * apareceram juntos:
     *
     * - **O agrupamento perde sentido.** Com 50 membros escolhendo livremente, o card vira uma
     *   fileira de emojis únicos, e "12 👍" — que é o ponto de ter contagem — nunca acontece.
     * - **Volta o problema das caixas vazias.** Emoji recente escolhido por quem tem Android novo
     *   aparece como ▯ para quem tem Android 12. A lista da V43 já enfrentou isso.
     * - **Um toque contra três.** Reação é o gesto mais barato do feed; abrir teclado, procurar e
     *   escolher custa mais que o próprio conteúdo vale.
     *
     * Os seis cobrem o que se quer dizer num app de treino: aprovação, força, intensidade,
     * aplauso, surpresa e carinho. Todos são Unicode antigo — nenhum vira caixa vazia.
     *
     * O banco **não** tem CHECK deste vocabulário, de propósito: a lista é decisão de produto e
     * vai mudar, e um CHECK exigiria migration a cada ajuste. Vocabulário aberto no banco, fechado
     * aqui — a técnica do `achievement_id` (#32) e do `type` das notificações (V42).
     */
    val REACOES: List<String> = listOf("👍", "💪", "🔥", "👏", "😮", "❤️")

    /** Recusa a reação fora do vocabulário. O banco aceita; quem decide o que é válido é isto. */
    fun reacaoValida(emoji: String): Boolean = emoji in REACOES

    /**
     * O texto normalizado, ou `null` se não serve.
     *
     * Espaço em volta some **antes** de medir: um comentário de 500 caracteres mais um espaço não
     * é um comentário grande demais, é o mesmo comentário. E só-espaços vira `null` — comentário
     * vazio é ruído no feed de 49 pessoas.
     *
     * Devolver o texto tratado, em vez de um booleano, é o que impede o serviço de validar uma
     * coisa e gravar outra. Foi a lição do `display_name`, onde o servidor normalizava e o cliente
     * exibia o que a pessoa digitou.
     */
    fun comentarioValido(texto: String): String? =
        texto.trim().takeIf { it.isNotEmpty() && it.length <= MAX_COMENTARIO }

    /**
     * Pode apagar este comentário? **Três pessoas** (decisão de 2026-09-06, emendada em 09-07).
     *
     * | Quem | Por quê |
     * |---|---|
     * | O **autor** | Escreveu e se arrependeu — erro de digitação não deveria virar pedido a terceiro |
     * | O **dono do check-in** | É a foto DELE. Quem publica decide o que fica pendurado no próprio conteúdo, como no Instagram |
     * | O **admin** | Modera o grupo; é a autoridade única (6.7) |
     *
     * A primeira versão tinha só autor e admin. O dono do check-in entrou depois, e a ordem em que
     * isso aconteceu importa: **acrescentar um caso não invalidou nada** do que já estava testado.
     *
     * **Sem prazo**, ao contrário do check-in (4.11). Lá o prazo existe porque apagar libera o slot
     * do dia e mexeria no ranking; comentário não vale ponto, então apagar não move nada — e
     * comentário ofensivo antigo precisa poder sair.
     */
    fun podeApagarComentario(
        souOAutor: Boolean,
        souDonoDoCheckIn: Boolean,
        meuPapel: MemberRole?,
    ): Boolean = souOAutor || souDonoDoCheckIn || meuPapel == MemberRole.ADMIN
}
