package dev.rafael.app.push

import android.app.PendingIntent
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import dev.rafael.app.MainActivity
import dev.rafael.app.R
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import org.koin.core.context.GlobalContext
import kotlin.random.Random

/**
 * Recebe o push (F.1).
 *
 * ## Dois momentos, e o segundo é o que costuma ser esquecido
 *
 * **`onMessageReceived`** — chega uma notificação. O Android já a mostra sozinho quando o app
 * está em background (porque o servidor manda o bloco `notification`), mas com o app ABERTO ele
 * não mostra nada: cabe a este método desenhar. Sem isso, quem estiver com o app na mão não vê o
 * pedido chegar.
 *
 * **`onNewToken`** — o FCM reemitiu o token, por conta própria. Isso acontece em reinstalação,
 * limpeza de dados, ou por decisão dele. Sem re-registrar aqui, o aparelho **para de receber
 * push em silêncio** e ninguém descobre até alguém reclamar.
 */
private const val TAG = "FitJourneyPush"

class FitJourneyMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        Log.i(TAG, "FCM reemitiu o token: ${token.take(12)}")
        // O registro precisa do usuário autenticado, e este serviço roda fora de qualquer tela.
        // Guardar o token e deixar o app registrá-lo no próximo boot é mais simples e suficiente:
        // o token novo só passa a valer quando o FCM o entrega, e a próxima abertura do app é
        // quase sempre antes da próxima notificação.
        TokenPendente.guardar(applicationContext, token)
    }

    override fun onMessageReceived(mensagem: RemoteMessage) {
        // Prova de que a mensagem CHEGOU ao aparelho. Sem esta linha, "não apareceu nada" tem duas
        // causas indistinguíveis — o push não chegou, ou chegou e não foi desenhado — e elas se
        // corrigem em lugares opostos (rede/FCM vs. canal/permissão).
        Log.i(TAG, "Push recebido: dados=${mensagem.data}, notification=${mensagem.notification != null}")

        // Avisa quem está com o app ABERTO, antes de desenhar. O `tryEmit` não bloqueia e não
        // falha quando ninguém escuta — ver `AvisosDePush`.
        //
        // Vem antes do `return` de mensagem sem título de propósito: um push só-dados (que este
        // app ainda não manda, mas pode vir de uma versão futura do servidor) não vira notificação
        // na bandeja e mesmo assim deve atualizar a tela de quem está olhando.
        runCatching {
            GlobalContext.get().get<AvisosDePush>().chegou(mensagem.data["tipo"].orEmpty())
        }

        val titulo = mensagem.notification?.title ?: mensagem.data["title"] ?: return
        val corpo = mensagem.notification?.body ?: mensagem.data["body"].orEmpty()

        // O `data` viaja para a Activity e é lá que vira navegação — este serviço não conhece
        // rotas, e não deveria: ele traduz push em notificação do sistema, só isso.
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            mensagem.data.forEach { (k, v) -> putExtra(k, v) }
        }

        val pending = PendingIntent.getActivity(
            this,
            // Request code único: com um fixo, o Android REUSARIA o PendingIntent anterior e o
            // segundo pedido abriria a tela com os dados do primeiro.
            Random.nextInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notificacao = NotificationCompat.Builder(this, CanalDeNotificacao.ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(titulo)
            .setContentText(corpo)
            .setAutoCancel(true)          // some da bandeja ao tocar
            .setContentIntent(pending)
            .build()

        // Se a permissão foi negada, isto lança SecurityException. Não é erro nosso e não deve
        // derrubar o processo: a notificação continua GRAVADA no servidor, e a central vai
        // mostrá-la quando a pessoa abrir o app. É a razão de a V42 existir.
        //
        // Mas a falha é REGISTRADA. A primeira versão engolia em silêncio, e na bateria isso
        // produziu o pior sintoma possível: o push chegava (havia log), a notificação não
        // aparecia, e não havia nada entre os dois para olhar. **Engolir exceção é decisão de
        // fluxo, não de log** — o processo segue, mas alguém precisa poder saber por quê.
        val gerente = NotificationManagerCompat.from(this)
        Log.i(
            TAG,
            "Desenhando notificação: habilitadas=${gerente.areNotificationsEnabled()}, " +
                "canal=${gerente.getNotificationChannel(CanalDeNotificacao.ID)?.importance ?: "AUSENTE"}",
        )

        runCatching { gerente.notify(Random.nextInt(), notificacao) }
            .onFailure { Log.w(TAG, "notify() falhou", it) }
    }
}
