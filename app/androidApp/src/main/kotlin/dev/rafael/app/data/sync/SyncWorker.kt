package dev.rafael.app.data.sync

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dev.rafael.app.data.session.HistoricoDeSessoes
import dev.rafael.core.database.outbox.ProcessadorDeOutbox
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

    private val sync: HistoricoDeSessoes by inject()
    private val outbox: ProcessadorDeOutbox by inject()

    /**
     * Cada etapa no SEU PRÓPRIO runCatching, de propósito.
     *
     * Num bloco só, uma exceção no histórico (que é o menos importante dos três) impediria a
     * fila de programas/treinos de ser processada — o dado do usuário ficaria preso por causa
     * de um relatório. Falha isolada vira retry no fim; nunca aborta o que vem depois.
     */
    override suspend fun doWork(): Result {
        var precisaTentarDeNovo = false

        if (!etapa("sessoes") { sync.flush() }) precisaTentarDeNovo = true

        // BAIXA o histórico: o banco local fica quente mesmo que o usuário nunca abra a tela
        // de Progresso online. É o único que pode falhar sem consequência para o usuário.
        etapa("historico") { sync.sincronizarHistorico() }

        // Fila de programas/treinos/agenda (ARCH #30, B.4). Vem DEPOIS das sessões: uma sessão
        // registra treino que já existe no servidor, então nunca depende da fila; o inverso
        // não vale.
        runCatching { outbox.processar() }.fold(
            onSuccess = {
                Log.i(TAG, "fila: $it")
                // TENTAR_DEPOIS não é erro: é "a rede caiu no meio da fila". O retry preserva
                // a ordem, que impede a agenda de subir antes do treino que ela referencia.
                if (it == ProcessadorDeOutbox.Resultado.TENTAR_DEPOIS) precisaTentarDeNovo = true
            },
            onFailure = {
                Log.e(TAG, "fila EXPLODIU", it)
                precisaTentarDeNovo = true
            },
        )

        return if (precisaTentarDeNovo) Result.retry() else Result.success()
    }

    /**
     * Executa uma etapa e **registra a exceção**.
     *
     * Sem isto o worker era indepurável: `runCatching` engolia tudo, o WorkManager só dizia
     * RETRY, e não havia como saber qual das três etapas falhou nem por quê. Custou uma noite
     * de teste manual descobrir isso — o log fica.
     *
     * @return true se a etapa passou.
     */
    private suspend fun etapa(nome: String, bloco: suspend () -> Unit): Boolean =
        runCatching { bloco() }.fold(
            onSuccess = { true },
            onFailure = { Log.e(TAG, "etapa '$nome' falhou", it); false },
        )

    companion object {
        /** `adb logcat -s FitJourneySync:*` mostra exatamente onde o sync parou. */
        const val TAG = "FitJourneySync"
        const val TRABALHO_UNICO = "sync-outbox-agora"
        const val TRABALHO_PERIODICO = "sync-outbox-periodico"
    }
}
