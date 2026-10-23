package dev.rafael.app.data.notificacoes

import dev.rafael.contract.notificacao.NotificacaoDto
import dev.rafael.core.database.SyncStamps
import dev.rafael.core.database.outbox.AgendadorDeSync
import dev.rafael.core.database.outbox.Outbox
import dev.rafael.core.database.outbox.ProcessadorDeOutbox
import dev.rafael.core.database.outbox.TipoOperacao
import dev.rafael.core.result.AppError
import dev.rafael.core.result.AppResult
import dev.rafael.core.result.asFailure
import dev.rafael.core.result.asSuccess

/**
 * CACHE-FIRST (ARCH #30 estendido às notificações, fatia "notificações offline", 2026-10-22).
 *
 * Antes `Notificacoes` era só-rede: offline, `listar()` falhava e a tela esvaziava. Agora:
 *  - `listar()` tenta a rede SEMPRE (a tela e o badge são o sincronizador -- entrar na tela,
 *    pull, push, "Tentar de novo" -- não há TTL que espera vencer); só cai no cache quando a
 *    rede FALHA por falta de conexão e já houve sincronização alguma vez com esta conta.
 *  - `marcarComoLidas()` grava local JÁ e enfileira o envio -- é otimista como `program`/
 *    `workout`, só que a mutação é GLOBAL ("marque tudo"), então o alvo do outbox é um id FIXO
 *    por conta ([ALVO_MARCAR_LIDAS]), não um id de notificação.
 *
 * [NotificacoesViewModel] e [ContadorDeNaoLidas] não mudaram: o contrato [Notificacoes] é
 * idêntico ao de antes, só a implementação por trás ganhou banco e fila.
 */
class NotificacoesRepositoryImpl(
    private val remote: NotificacoesRemota,
    private val local: NotificacaoLocalDataSource,
    private val stamps: SyncStamps,
    private val outbox: Outbox,
    private val agendador: AgendadorDeSync,
    /** Caminho de fundo #2 do outbox -- mesmo motivo do `ProgramRepositoryImpl`: o WorkManager
     * sozinho não basta quando o sistema decide não acordá-lo. */
    private val processador: ProcessadorDeOutbox,
) : Notificacoes {

    override suspend fun listar(): AppResult<List<NotificacaoDto>> {
        // ENVIAR ANTES DE BAIXAR: baixar a verdade do servidor antes de mandar o que eu tenho
        // seria a ordem errada -- o save() teria que proteger uma marcação que já poderia ter
        // subido. Falha em silêncio de propósito: sem rede, o próprio listar() abaixo falha
        // sozinho com a mensagem certa.
        runCatching { processador.processar() }

        return when (val net = remote.listar()) {
            is AppResult.Success -> {
                val marcacaoPendente = outbox.alvosPendentes().contains(ALVO_MARCAR_LIDAS)
                local.save(net.value, marcarTodasComoLidas = marcacaoPendente)
                stamps.marcar(SyncStamps.NOTIFICACOES)
                local.ler().asSuccess()
            }
            is AppResult.Failure -> {
                // Mesmas DUAS condições do ProgramRepositoryImpl: falha de TRANSPORTE (não é
                // resposta real do servidor) e já ter sincronizado alguma vez com esta conta
                // (senão "nada guardado" seria indistinguível de "nunca baixei").
                val podeServirCache = net.error is AppError.Connection &&
                    stamps.jaSincronizou(SyncStamps.NOTIFICACOES)
                if (podeServirCache) local.ler().asSuccess() else net.error.asFailure()
            }
        }
    }

    /**
     * Marca local JÁ -- o badge e a tela não esperam a rede -- e enfileira o POST.
     *
     * A rota real não recebe id (é "marque tudo"), então não há um id de notificação para ser o
     * alvo do outbox: usa-se [ALVO_MARCAR_LIDAS], fixo. Isso também responde à pergunta de
     * compactação: duas marcações enfileiradas offline (ex. abrir a tela duas vezes) colapsam
     * numa só pelo mesmo alvo -- o `CompactadorDeOutbox` não precisou de mudança nenhuma.
     */
    override suspend fun marcarComoLidas(): AppResult<Unit> {
        local.marcarTodasComoLidasLocal()
        outbox.enfileirar(TipoOperacao.MARCAR_NOTIFICACAO_LIDA, ALVO_MARCAR_LIDAS)
        agendador.agendar()
        return Unit.asSuccess()
    }

    override suspend fun registrarDispositivo(token: String): AppResult<Unit> =
        remote.registrarDispositivo(token)

    override suspend fun darBaixaNoDispositivo(token: String): AppResult<Unit> =
        remote.darBaixaNoDispositivo(token)

    companion object {
        const val ALVO_MARCAR_LIDAS = "todas"
    }
}
