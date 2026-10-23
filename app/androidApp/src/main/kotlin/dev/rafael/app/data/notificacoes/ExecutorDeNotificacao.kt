package dev.rafael.app.data.notificacoes

import dev.rafael.core.database.outbox.ExecutorDeOperacao
import dev.rafael.core.database.outbox.Operacao
import dev.rafael.core.database.outbox.TipoOperacao
import dev.rafael.core.result.AppError
import dev.rafael.core.result.AppResult
import dev.rafael.core.result.asFailure

/**
 * Traduz `MARCAR_NOTIFICACAO_LIDA` da fila num `POST /me/notifications/read` (ARCH #30).
 *
 * Sem payload para decodificar -- a operação é só o tipo, o alvo fixo (ver
 * `NotificacoesRepositoryImpl.ALVO_MARCAR_LIDAS`) nem é lido aqui. Mais simples que
 * `ExecutorDePrograma`/`ExecutorDeTreino` porque não há id de servidor envolvido.
 */
class ExecutorDeNotificacao(
    private val remote: NotificacoesRemota,
) : ExecutorDeOperacao {

    override val tipos = setOf(TipoOperacao.MARCAR_NOTIFICACAO_LIDA)

    override suspend fun executar(operacao: Operacao): AppResult<Unit> = when (operacao.tipo) {
        TipoOperacao.MARCAR_NOTIFICACAO_LIDA -> remote.marcarComoLidas()
        else -> AppError.Unexpected("Tipo fora deste executor: ${operacao.tipo}").asFailure()
    }
}
