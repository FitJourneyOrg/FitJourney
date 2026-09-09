package dev.rafael.server.features.notificacao.services

import kotlin.uuid.Uuid

/**
 * Manda push para os aparelhos de alguém (fatia F.1).
 *
 * ## Interface, e não classe concreta
 *
 * O `FirebaseMessaging` é estático e fala com a rede. Um serviço que o chamasse direto seria
 * impossível de testar sem internet, e o que precisa de teste aqui **não é o envio**: é a decisão de
 * QUANDO enviar e PARA QUEM.
 *
 * ## Recebe texto pronto, e nunca soube de idioma (G.1, ARCH #37)
 *
 * O parâmetro é [AvisoRenderizado], não [Aviso]. Quem escolhe as palavras é o `NotificacaoService`,
 * uma camada acima. Este arquivo despacha bytes.
 */
interface Notificador {

    /**
     * Notifica todos os aparelhos de [destinatario]. **Nunca lança.**
     *
     * Falha de push é registrada e engolida de propósito: o pedido de amizade já foi criado, e
     * derrubá-lo porque o Google está fora seria trocar um problema pequeno (aviso que não chegou)
     * por um grande (a ação do usuário não aconteceu).
     *
     * O badge da tela de Amigos é o piso: quem não recebe push ainda vê o pedido ao abrir.
     */
    suspend fun notificar(destinatario: Uuid, aviso: AvisoRenderizado)
}

/**
 * O que aconteceu, **sem as palavras** (G.1, ARCH #37).
 *
 * ## Por que este objeto deixou de carregar título e corpo
 *
 * Antes as sete factories daqui montavam a frase em português. Ao passar a existir mais de um
 * idioma, a alternativa óbvia seria `Aviso.pedidoDeAmizade(de, deId, idioma)`, e o custo dela
 * aparece uma linha adiante: **todo serviço que notifica precisaria descobrir o idioma do
 * destinatário**. Hoje são quatro portas estreitas, e a quinta nasceria já tendo que saber disso.
 *
 * > **Concern que aparece em quatro lugares está no lugar errado nos quatro.**
 *
 * O `NotificacaoService` já lê o usuário para pegar os tokens de aparelho. É a borda natural: uma
 * leitura, um lugar que sabe de idioma, features intocadas.
 *
 * ## Por que `dados` existe além da chave
 *
 * O Android mostra título e corpo; o `dados` é o que o app lê ao ser aberto pela notificação, e é
 * ele que faz o toque abrir a tela certa em vez da Home (o deep link).
 *
 * Mandar SÓ dados (silent push) e deixar o app montar a notificação seria mais flexível, e resolveria
 * a tradução de graça. **É errado aqui**: mensagem só-dados não aparece se o app estiver fechado ou
 * dormindo, que é exatamente quando a notificação importa. É por causa disso que o servidor tem um
 * catálogo de texto, e é a única exceção à regra de que texto vive no cliente.
 *
 * O `tipo` NÃO está em [dados]: ele é derivado da [chave] no momento de renderizar. Antes era escrito
 * à mão em cada factory, ao lado da chave, e nada impedia que os dois discordassem.
 */
data class Aviso(
    val chave: ChaveDeAviso,
    val dados: Map<String, String> = emptyMap(),
) {
    companion object {

        /**
         * 35.x: "Fulano quer ser seu amigo" (decisão de 2026-08-27).
         *
         * O id vai junto para o app poder abrir o PERFIL de quem pediu, se um dia a tela quiser
         * isso. Hoje o deep link só usa o tipo, mas um dado que já existe no servidor é barato de
         * mandar, e buscá-lo depois exigiria uma requisição a partir de uma tela que ainda não abriu.
         */
        fun pedidoDeAmizade(de: String, deId: Uuid) = Aviso(
            chave = ChaveDeAviso.PedidoDeAmizade(nome = de),
            dados = mapOf(FROM_USER_ID to deId.toString()),
        )

        /**
         * 10.4: comentário no meu check-in.
         *
         * **O corte do trecho acontece AQUI, não no catálogo.** O corpo traz o começo do texto
         * porque a pessoa decide se abre agora pelo que foi dito, e um aviso que obriga a abrir para
         * saber do que se trata é um aviso que ela aprende a ignorar.
         *
         * Cortar aqui, e não em cada idioma, é o que garante que o limite da bandeja é um só. Se
         * cada tradução cortasse, a primeira que esquecesse entregaria um push truncado pelo sistema
         * no meio de uma palavra.
         */
        fun comentarioNoMeuCheckIn(de: String, texto: String, groupId: Uuid, checkInId: Uuid) = Aviso(
            chave = ChaveDeAviso.ComentarioNoMeuCheckIn(
                nome = de,
                trecho = texto.take(PREVIA).let { if (texto.length > PREVIA) "$it…" else it },
            ),
            dados = mapOf(GROUP_ID to groupId.toString(), CHECK_IN_ID to checkInId.toString()),
        )

        /** 10.4: denúncia contra mim. Sem denunciante e sem motivo, por desenho (ver a chave). */
        fun denunciaContraMim(groupId: Uuid, ehComentario: Boolean) = Aviso(
            chave = ChaveDeAviso.DenunciaContraMim(ehComentario = ehComentario),
            dados = mapOf(GROUP_ID to groupId.toString()),
        )

        /** 10.4: nova denúncia no meu grupo, só para o admin. Uma por CASO, não por denúncia (6.11). */
        fun novaDenunciaNoGrupo(grupo: String, groupId: Uuid) = Aviso(
            chave = ChaveDeAviso.NovaDenunciaNoGrupo(grupo = grupo),
            dados = mapOf(GROUP_ID to groupId.toString()),
        )

        /** 10.4: check-in invalidado. O MESMO aviso pelo julgamento e pela invalidação direta (6.10). */
        fun checkInInvalidado(grupo: String, groupId: Uuid) = Aviso(
            chave = ChaveDeAviso.CheckInInvalidado(grupo = grupo),
            dados = mapOf(GROUP_ID to groupId.toString()),
        )

        /**
         * 10.6: entradas do dia, AGREGADAS. No máximo uma por pessoa por dia.
         *
         * Uma por entrada teria a mesma aritmética que matou a votação: as entradas acontecem em
         * rajada na janela `AGENDADO`, e num desafio de 50 pessoas o primeiro a entrar receberia 49
         * avisos, **1.225 no total, todos dizendo a mesma coisa**.
         */
        fun entradasDoDia(grupo: String, groupId: Uuid, quantas: Int, souOCriador: Boolean) = Aviso(
            chave = ChaveDeAviso.EntradasDoDia(grupo, quantas, souOCriador),
            dados = mapOf(GROUP_ID to groupId.toString()),
        )

        /** A fila do admin está parada há 3 dias ou mais (emenda de 2026-09-07). */
        fun filaParada(grupo: String, groupId: Uuid, casos: Int, dias: Int) = Aviso(
            chave = ChaveDeAviso.FilaParada(grupo, casos, dias),
            dados = mapOf(GROUP_ID to groupId.toString()),
        )

        /** Quanto do comentário cabe na bandeja antes de virar reticências. */
        private const val PREVIA = 120

        /**
         * A chave que o cliente lê para decidir qual tela abrir.
         *
         * O VALOR vem de `ChaveDeAviso.tipo` e é posto no `dados` na hora de renderizar. A constante
         * continua aqui porque é o nome do campo no payload do FCM, e mudá-lo quebra o cliente.
         */
        const val TIPO = "tipo"

        const val GROUP_ID = "groupId"
        const val CHECK_IN_ID = "checkInId"
        const val FROM_USER_ID = "fromUserId"
    }
}

/**
 * O aviso já com palavras, pronto para gravar e despachar (G.1).
 *
 * ## [INV] Existe UMA renderização por aviso
 *
 * A linha gravada em `notifications` e o push do FCM saem deste mesmo objeto. Antes os dois liam o
 * mesmo `Aviso` por disciplina, e quem escrevesse um caminho que renderizasse duas vezes não
 * quebraria teste nenhum. Agora vale por construção.
 *
 * Isso reforça o `[INV]` do ARCH #36 (título e corpo são gravados JÁ renderizados) em vez de
 * competir com ele: **notificação é registro do que houve, não consulta ao presente.** Trocar de
 * idioma amanhã não reescreve o que já está na central, e isso está certo.
 */
data class AvisoRenderizado(
    val tipo: String,
    val titulo: String,
    val corpo: String,
    val dados: Map<String, String>,
)
