package dev.rafael.app.data.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dev.rafael.app.data.session.SessionSync
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Esvazia a outbox de sessões em background. É o "despertador" do offline-first: o outbox
 * guarda o que está pendente, o WorkManager decide QUANDO tentar de novo.
 *
 * Roda mesmo com o app fechado e só quando há rede (constraint no agendador). Antes disto,
 * um treino feito offline só subia se o usuário abrisse o app de novo.
 *
 * Usa KoinComponent para injetar o SessionSync — evita ter que instalar um WorkerFactory
 * customizado só por causa de uma dependência.
 */
class SyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params), KoinComponent {

    private val sync: SessionSync by inject()

    override suspend fun doWork(): Result =
        runCatching {
            sync.flush()                  // sobe o que foi feito offline
            sync.sincronizarHistorico()   // e BAIXA o histórico: o banco local fica quente
            // mesmo que o usuário nunca abra a tela de Progresso online.
        }.fold(
            onSuccess = { Result.success() },
            // falhou (rede instável, 5xx): o WorkManager reagenda com backoff exponencial
            onFailure = { Result.retry() },
        )

    companion object {
        const val TRABALHO_UNICO = "sync-outbox-agora"
        const val TRABALHO_PERIODICO = "sync-outbox-periodico"
    }
}
