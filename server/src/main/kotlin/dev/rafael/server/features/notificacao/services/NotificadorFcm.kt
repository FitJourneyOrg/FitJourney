package dev.rafael.server.features.notificacao.services

import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingException
import com.google.firebase.messaging.MessagingErrorCode
import com.google.firebase.messaging.MulticastMessage
import com.google.firebase.messaging.Notification
import dev.rafael.core.result.AppResult
import dev.rafael.server.features.notificacao.db.DeviceTokenRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import kotlin.uuid.Uuid

/**
 * Envio real via FCM (fatia F.1).
 *
 * ## O que este arquivo faz de mais importante: LIMPAR TOKEN MORTO
 *
 * O FCM devolve, por token, se ele ainda vale. `UNREGISTERED` e `INVALID_ARGUMENT` significam que
 * aquele aparelho não existe mais para nós — app desinstalado, dados limpos, token reemitido.
 *
 * **Sem apagar, a tabela cresce para sempre com lixo**, e cada envio fica mais caro carregando
 * aparelhos que nunca vão receber nada. É a mesma ideia da purga de mídia da fatia B: o sistema
 * precisa saber jogar fora o que ele mesmo produziu.
 *
 * A limpeza acontece **na resposta do envio**, não num job agendado — porque é exatamente aqui
 * que a informação existe, e um job seria mais uma peça falhando em silêncio.
 */
class NotificadorFcm(
    private val tokens: DeviceTokenRepository,
    private val fcm: () -> FirebaseMessaging = { FirebaseMessaging.getInstance() },
) : Notificador {

    private val log = LoggerFactory.getLogger(NotificadorFcm::class.java)

    override suspend fun notificar(destinatario: Uuid, aviso: Aviso) {
        val alvos = when (val r = tokens.doUsuario(destinatario)) {
            is AppResult.Success -> r.value
            is AppResult.Failure -> {
                log.warn("Push: não consegui ler os tokens de {}: {}", destinatario, r.error)
                return
            }
        }

        // Ninguém instalou o app ou ninguém deu permissão. Não é erro — é o caso comum no começo.
        if (alvos.isEmpty()) return

        runCatching { enviar(alvos, aviso) }
            .onFailure {
                // NUNCA propaga: o pedido de amizade já foi criado, e derrubá-lo porque o Google
                // está fora seria trocar um problema pequeno por um grande.
                log.warn("Push para {} falhou inteiro: {}", destinatario, it.message)
            }
    }

    private suspend fun enviar(alvos: List<String>, aviso: Aviso) = withContext(Dispatchers.IO) {
        val mensagem = MulticastMessage.builder()
            .setNotification(
                Notification.builder()
                    .setTitle(aviso.titulo)
                    .setBody(aviso.corpo)
                    .build(),
            )
            .putAllData(aviso.dados)
            .addAllTokens(alvos)
            .build()

        val resposta = fcm().sendEachForMulticast(mensagem)

        // O índice da resposta casa com o índice do token enviado — é assim que se sabe QUAL
        // aparelho morreu.
        //
        // O log nomeia a CAUSA e o token, e isso não é verbosidade: a primeira versão dizia só
        // "apagando 1 token(s) morto(s)", e quando o token recém-registrado do aparelho de teste
        // foi apagado no primeiro envio não havia como saber se era `UNREGISTERED` (aparelho
        // fora) ou `INVALID_ARGUMENT` (credencial de outro projeto Firebase) — dois problemas
        // com causas opostas e o mesmo sintoma. **Ação destrutiva sem motivo registrado não é
        // diagnosticável.**
        val mortos = resposta.responses.withIndex().mapNotNull { (i, r) ->
            val causa = r.exception?.messagingErrorCode
            val morto = causa == MessagingErrorCode.UNREGISTERED ||
                causa == MessagingErrorCode.INVALID_ARGUMENT
            if (!morto) return@mapNotNull null

            log.info(
                "Push: apagando token {}… ({}): {}",
                alvos[i].take(12),
                causa,
                r.exception?.message,
            )
            alvos[i]
        }

        if (mortos.isNotEmpty()) tokens.apagar(mortos)

        // Falha que NÃO é token morto (rede, quota, indisponibilidade) fica só no log: o token
        // continua válido e a próxima notificação tenta de novo. Apagá-lo aqui seria perder o
        // aparelho de alguém por causa de uma instabilidade passageira.
        resposta.responses
            .mapNotNull { it.exception }
            .filterNot {
                it.messagingErrorCode == MessagingErrorCode.UNREGISTERED ||
                    it.messagingErrorCode == MessagingErrorCode.INVALID_ARGUMENT
            }
            .forEach {
                log.warn(
                    "Push falhou num aparelho ({}): {}",
                    (it as FirebaseMessagingException).messagingErrorCode,
                    it.message,
                )
            }
    }
}
