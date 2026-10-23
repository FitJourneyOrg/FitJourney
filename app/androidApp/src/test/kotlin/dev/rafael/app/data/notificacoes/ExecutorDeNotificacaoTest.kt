package dev.rafael.app.data.notificacoes

import dev.rafael.contract.notificacao.NotificacaoDto
import dev.rafael.core.database.outbox.Operacao
import dev.rafael.core.database.outbox.TipoOperacao
import dev.rafael.core.result.AppError
import dev.rafael.core.result.AppResult
import dev.rafael.core.result.asFailure
import dev.rafael.core.result.asSuccess
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** `ExecutorDeNotificacao`: só o roteamento tipo→chamada HTTP importa aqui (ARCH #30). */
class ExecutorDeNotificacaoTest {

    private class FakeRemota(private val resultado: AppResult<Unit> = Unit.asSuccess()) : NotificacoesRemota {
        var marcarComoLidasChamadas = 0
        override suspend fun listar(): AppResult<List<NotificacaoDto>> = emptyList<NotificacaoDto>().asSuccess()
        override suspend fun marcarComoLidas(): AppResult<Unit> {
            marcarComoLidasChamadas++
            return resultado
        }
        override suspend fun registrarDispositivo(token: String): AppResult<Unit> = Unit.asSuccess()
        override suspend fun darBaixaNoDispositivo(token: String): AppResult<Unit> = Unit.asSuccess()
    }

    private fun operacao(tipo: TipoOperacao) = Operacao(seq = 1, tipo = tipo, alvoId = "todas", payload = "")

    @Test
    fun `declara atender exatamente MARCAR_NOTIFICACAO_LIDA`() {
        val executor = ExecutorDeNotificacao(FakeRemota())

        assertEquals(setOf(TipoOperacao.MARCAR_NOTIFICACAO_LIDA), executor.tipos)
    }

    @Test
    fun `MARCAR_NOTIFICACAO_LIDA chama remote-marcarComoLidas e devolve o resultado dela`() = runTest {
        val remota = FakeRemota()
        val executor = ExecutorDeNotificacao(remota)

        val resultado = executor.executar(operacao(TipoOperacao.MARCAR_NOTIFICACAO_LIDA))

        assertEquals(1, remota.marcarComoLidasChamadas)
        assertTrue(resultado is AppResult.Success)
    }

    /** Caminho de falha: o executor não engole o erro do servidor, repassa como veio. */
    @Test
    fun `propaga a falha da rede sem reescrever o erro`() = runTest {
        val erro = AppError.Forbidden(message = "Conta suspensa")
        val executor = ExecutorDeNotificacao(FakeRemota(resultado = erro.asFailure()))

        val resultado = executor.executar(operacao(TipoOperacao.MARCAR_NOTIFICACAO_LIDA))

        assertTrue(resultado is AppResult.Failure)
        assertEquals(erro, (resultado as AppResult.Failure).error)
    }

    /** Tipo fora deste executor: nunca deveria acontecer (o processador roteia por `tipos`),
     *  mas o `else` existe e precisa devolver falha, não lançar nem silenciar. */
    @Test
    fun `tipo fora do executor devolve falha ao inves de lancar`() = runTest {
        val executor = ExecutorDeNotificacao(FakeRemota())

        val resultado = executor.executar(operacao(TipoOperacao.DEFINIR_AGENDA))

        assertTrue(resultado is AppResult.Failure)
        assertTrue((resultado as AppResult.Failure).error is AppError.Unexpected)
    }
}
