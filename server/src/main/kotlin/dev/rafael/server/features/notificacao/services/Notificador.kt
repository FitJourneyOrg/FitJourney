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

        /** A chave que o cliente lê para decidir qual tela abrir. */
        const val TIPO = "tipo"
        const val TIPO_PEDIDO_DE_AMIZADE = "PEDIDO_DE_AMIZADE"
    }
}
