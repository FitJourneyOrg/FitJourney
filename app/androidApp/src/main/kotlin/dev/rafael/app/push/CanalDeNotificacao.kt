package dev.rafael.app.push

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.content.getSystemService

/**
 * O canal de notificação do app (F.1).
 *
 * ## Sem canal, a notificação NÃO APARECE — e não dá erro
 *
 * A partir do Android 8 toda notificação precisa de um canal registrado. Se o canal não existir,
 * o sistema **descarta a notificação em silêncio**: nada na tela, nada no log do app, nada em
 * lugar nenhum. É o tipo de falha que consome uma tarde inteira porque não deixa rastro.
 *
 * Por isso o registro roda no `onCreate` da Application, e não na primeira notificação: criar
 * canal é idempotente e barato, e adiá-lo só cria uma janela em que o push chega e some.
 */
object CanalDeNotificacao {

    /** Um canal só, por enquanto. Tipos novos (comentário, reação) podem querer o próprio. */
    const val ID = "fitjourney_geral"

    fun registrar(contexto: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val canal = NotificationChannel(
            ID,
            "Notificações",
            // DEFAULT e não HIGH: pedido de amizade não é urgente. HIGH abriria a notificação
            // sobre a tela (heads-up) e interromperia quem está no meio de um treino — que é
            // exatamente quando o app está aberto.
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "Pedidos de amizade e avisos do app"
        }

        contexto.getSystemService<NotificationManager>()?.createNotificationChannel(canal)
    }
}
