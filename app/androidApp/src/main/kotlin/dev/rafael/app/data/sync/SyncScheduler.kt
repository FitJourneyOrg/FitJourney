package dev.rafael.app.data.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Agenda o flush da outbox. Duas frentes:
 *
 *  - `agendarAgora()`  — disparado quando fica algo pendente (ex.: treinou offline). O Android
 *    executa assim que houver rede; com backoff exponencial se falhar.
 *  - `agendarPeriodico()` — rede de segurança a cada 6h, caso o app fique muito tempo fechado.
 *
 * Ambos são únicos por nome: chamar várias vezes não empilha trabalho duplicado.
 */
class SyncScheduler(private val context: Context) {

    private val comRede = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    fun agendarAgora() {
        val pedido = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(comRede)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            SyncWorker.TRABALHO_UNICO,
            ExistingWorkPolicy.REPLACE,   // já havia um agendado? o novo cobre tudo (a outbox é a fila)
            pedido,
        )
    }

    fun agendarPeriodico() {
        val pedido = PeriodicWorkRequestBuilder<SyncWorker>(6, TimeUnit.HOURS)
            .setConstraints(comRede)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            SyncWorker.TRABALHO_PERIODICO,
            ExistingPeriodicWorkPolicy.KEEP,   // não reinicia o ciclo a cada abertura do app
            pedido,
        )
    }
}
