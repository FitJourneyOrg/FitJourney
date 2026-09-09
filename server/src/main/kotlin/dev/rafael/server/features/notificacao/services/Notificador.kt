package dev.rafael.server.features.notificacao.services

import kotlin.uuid.Uuid

/**
 * Manda push para os aparelhos de alguém (fatia F.1).
 *
 * ## Interface, e não classe concreta
 *
 * O `FirebaseMessaging` é estático e fala com a rede. Um serviço que o chamasse direto seria
 * impossível de testar sem internet — e o que precisa de teste aqui **não é o envio**, é a
 * decisão de QUANDO enviar e PARA QUEM. Essa decisão fica no `FriendshipService`, e ele recebe
 * esta interface.
 */
interface Notificador {

    /**
     * Notifica todos os aparelhos de [destinatario]. **Nunca lança.**
     *
     * Falha de push é registrada e engolida de propósito: o pedido de amizade já foi criado, e
     * derrubá-lo porque o Google está fora seria trocar um problema pequeno (aviso que não chegou)
     * por um grande (a ação do usuário não aconteceu).
     *
     * O badge da tela de Amigos é o piso — quem não recebe push ainda vê o pedido ao abrir.
     */
    suspend fun notificar(destinatario: Uuid, aviso: Aviso)
}

/**
 * O que uma notificação carrega.
 *
 * ## Por que `dados` existe além de título e corpo
 *
 * O Android mostra `titulo`/`corpo`; o `dados` é o que o app lê ao ser aberto pela notificação —
 * é ele que faz o toque abrir a tela de pedidos em vez da Home (o deep link).
 *
 * Mandar SÓ dados (silent push) e deixar o app montar a notificação seria mais flexível, e é
 * errado aqui: mensagem só-dados **não aparece se o app estiver fechado ou dormindo**, que é
 * exatamente quando a notificação importa.
 */
data class Aviso(
    val titulo: String,
    val corpo: String,
    val dados: Map<String, String> = emptyMap(),
) {
    companion object {
        /**
         * "Fulano quer ser seu amigo" (decisão de 2026-08-27).
         *
         * O NOME no corpo é deliberado: é o que faz a pessoa decidir se abre agora ou depois. Ele
         * já é público pela 9.3-A, então não há vazamento novo — mas aparece na tela bloqueada de
         * quem estiver com o celular na mesa, e isso foi pesado e aceito.
         */
        fun pedidoDeAmizade(de: String, deId: Uuid) = Aviso(
            titulo = "Novo pedido de amizade",
            corpo = "$de quer ser seu amigo",
            dados = mapOf(
                TIPO to TIPO_PEDIDO_DE_AMIZADE,
                // O id vai junto para o app poder abrir o PERFIL de quem pediu, se um dia a tela
                // quiser isso. Hoje o deep link só usa o TIPO — mas um dado que já existe no
                // servidor é barato de mandar, e buscá-lo depois exigiria uma requisição.
                "fromUserId" to deId.toString(),
            ),
        )

        // ---- fatia F: os gatilhos que dependiam das fatias E ----

        /**
         * 10.4: **comentário no meu check-in**. Só o dono recebe.
         *
         * Não há aviso para quem já comentou antes ("seguir a thread"). É a mesma aritmética que
         * matou a votação: dez pessoas comentando gerariam 55 notificações, e ninguém pediu para
         * acompanhar — quem postou pediu, ao postar.
         *
         * O corpo traz o **começo do texto**, não só "alguém comentou". A pessoa decide se abre
         * agora pelo que foi dito, e um aviso que obriga a abrir para saber do que se trata é um
         * aviso que ela aprende a ignorar.
         */
        fun comentarioNoMeuCheckIn(de: String, texto: String, groupId: Uuid, checkInId: Uuid) = Aviso(
            titulo = "$de comentou no seu check-in",
            corpo = texto.take(PREVIA).let { if (texto.length > PREVIA) "$it…" else it },
            dados = mapOf(
                TIPO to TIPO_COMENTARIO,
                GROUP_ID to groupId.toString(),
                CHECK_IN_ID to checkInId.toString(),
            ),
        )

        /**
         * 10.4: **denúncia contra mim**.
         *
         * **Não diz quem denunciou** — a mesma decisão da fila do admin (2026-09-07). Contar aqui
         * seria pior que contar lá: quem foi denunciado sabendo por quem tem motivo e oportunidade
         * de retaliar dentro do próprio grupo.
         *
         * Também **não diz o motivo**: o texto foi escrito para o admin julgar, não para o
         * denunciado ler. Repassá-lo transformaria a denúncia num canal de mensagem anônima.
         */
        fun denunciaContraMim(groupId: Uuid, ehComentario: Boolean) = Aviso(
            titulo = "Seu ${if (ehComentario) "comentário" else "check-in"} foi denunciado",
            corpo = "O admin do desafio vai avaliar. Seu ponto continua valendo até lá.",
            dados = mapOf(TIPO to TIPO_DENUNCIA_CONTRA_MIM, GROUP_ID to groupId.toString()),
        )

        /**
         * 10.4, quarto gatilho: **nova denúncia no meu grupo** — só o admin.
         *
         * Este existe porque os outros três são para o dono do conteúdo. Sem ele, ninguém avisa o
         * admin de que há algo para julgar, e como ele **não tem prazo** (6.12) a fila dependeria
         * de ele abrir a tela por acaso.
         *
         * Uma por **caso novo**, não por denúncia (6.11): a segunda pessoa denunciando o mesmo
         * alvo não gera aviso, porque o admin já sabe que há algo ali. Espelha o badge, que também
         * conta casos.
         */
        fun novaDenunciaNoGrupo(grupo: String, groupId: Uuid) = Aviso(
            titulo = "Nova denúncia em $grupo",
            corpo = "Tem algo esperando sua avaliação.",
            dados = mapOf(TIPO to TIPO_DENUNCIA_NO_GRUPO, GROUP_ID to groupId.toString()),
        )

        /**
         * 10.4: **check-in invalidado**.
         *
         * O corpo diz a consequência, não o veredito: "perdeu o ponto" é o que muda para a pessoa.
         * E não há recurso (6.7), então o texto não sugere um — oferecer resposta a quem não pode
         * responder é pior que o silêncio.
         *
         * ## O texto NÃO menciona denúncia, e isso foi corrigido na bateria
         *
         * A primeira versão dizia *"o admin avaliou **a denúncia**"* — escrita pensando no
         * julgamento e reusada na invalidação direta (6.10), onde **não existe denúncia nenhuma**.
         * Quem recebesse pelo caminho direto procuraria uma acusação que nunca houve.
         *
         * A decisão de os dois caminhos mandarem o MESMO aviso continua certa: quem perde o ponto
         * não precisa saber por qual rota o admin passou. O erro era o texto **afirmar um fato**
         * que só valia num deles.
         *
         * > **Mensagem compartilhada só pode afirmar o que é verdade em todos os caminhos que a
         * > usam.** Um aviso reusado herda a obrigação de ser verdadeiro em cada um.
         */
        fun checkInInvalidado(grupo: String, groupId: Uuid) = Aviso(
            titulo = "Check-in invalidado em $grupo",
            corpo = "O admin do desafio invalidou este check-in. Ele não conta mais ponto no ranking.",
            dados = mapOf(TIPO to TIPO_CHECK_IN_INVALIDADO, GROUP_ID to groupId.toString()),
        )

        /**
         * 10.6: **entradas do dia, AGREGADAS**. No máximo uma por pessoa por dia.
         *
         * Uma por entrada teria a mesma aritmética que matou a votação: os convites saem juntos, as
         * entradas acontecem em rajada na janela `AGENDADO`, e num desafio de 50 pessoas o primeiro
         * a entrar receberia 49 avisos — **1.225 no total, todos dizendo a mesma coisa**.
         *
         * Agregado por dia, o teto é uma notificação por pessoa por dia, **independentemente do
         * tamanho do grupo**.
         */
        /**
         * ## "SEU desafio" só para quem o criou (emenda de 2026-09-08, achada na bateria)
         *
         * A primeira versão dizia *"no **seu** desafio"* para todo mundo. Para os 49 que entraram
         * num desafio alheio, o possessivo é falso: eles participam, não são donos.
         *
         * O critério é **quem criou**, não quem é admin hoje. O cargo é transferível (2.12), e
         * depois de uma transferência o novo admin não fundou nada — mas quem fundou continua
         * sendo quem fundou. Mesma razão do filtro de contagem.
         */
        fun entradasDoDia(grupo: String, groupId: Uuid, quantas: Int, souOCriador: Boolean) = Aviso(
            titulo = grupo,
            corpo = buildString {
                append(if (quantas == 1) "1 pessoa entrou" else "$quantas pessoas entraram")
                append(if (souOCriador) " no seu desafio hoje." else " no desafio hoje.")
            },
            dados = mapOf(TIPO to TIPO_ENTRADAS_DO_DIA, GROUP_ID to groupId.toString()),
        )

        /**
         * Emenda de 2026-09-07: **a fila do admin está parada há 3 dias ou mais**.
         *
         * Existe porque a expiração automática de denúncia foi **recusada**: se o caso sumisse
         * sozinho, a inação do admin viraria absolvição. O remédio é fazê-lo ver, não apagar o que
         * ele não viu.
         *
         * O corpo diz **há quanto tempo**, não quantos casos — o que constrange é o tempo, e é ele
         * que o admin controla.
         */
        fun filaParada(grupo: String, groupId: Uuid, casos: Int, dias: Int) = Aviso(
            titulo = "Denúncias esperando em $grupo",
            corpo = if (casos == 1) {
                "Tem 1 caso parado há $dias dias."
            } else {
                "Tem $casos casos parados há $dias dias."
            },
            dados = mapOf(TIPO to TIPO_FILA_PARADA, GROUP_ID to groupId.toString()),
        )

        /** Quanto do comentário cabe na bandeja antes de virar reticências. */
        private const val PREVIA = 120

        /** A chave que o cliente lê para decidir qual tela abrir. */
        const val TIPO = "tipo"

        /**
         * O grupo e o check-in, para o deep link (fatia F).
         *
         * Vão no `dados` porque **o destino precisa deles e buscá-los depois seria uma requisição
         * a partir de uma tela que ainda não abriu**. O `fromUserId` do pedido de amizade já tinha
         * aberto esse precedente: dado que já existe no servidor é barato de mandar.
         */
        const val GROUP_ID = "groupId"
        const val CHECK_IN_ID = "checkInId"

        const val TIPO_PEDIDO_DE_AMIZADE = "PEDIDO_DE_AMIZADE"
        const val TIPO_COMENTARIO = "COMENTARIO_NO_CHECKIN"
        const val TIPO_DENUNCIA_CONTRA_MIM = "DENUNCIA_CONTRA_MIM"
        const val TIPO_DENUNCIA_NO_GRUPO = "DENUNCIA_NO_GRUPO"
        const val TIPO_CHECK_IN_INVALIDADO = "CHECK_IN_INVALIDADO"
        const val TIPO_ENTRADAS_DO_DIA = "ENTRADAS_DO_DIA"
        const val TIPO_FILA_PARADA = "FILA_PARADA"
    }
}
