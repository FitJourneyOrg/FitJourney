package dev.rafael.core.database.outbox

import dev.rafael.core.result.AppError
import dev.rafael.core.result.AppResult
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * O processador decide o que acontece quando o envio FALHA — e é aí que mora o risco de perder
 * dado do usuário. Testado contra um fake do Outbox, sem banco e sem rede.
 */
class ProcessadorDeOutboxTest {

    // --- fakes ---

    private class OutboxFalso(var fila: List<Operacao>) : FilaDeSaida {
        val concluidos = mutableListOf<String>()
        val permanentes = mutableListOf<Pair<String, String>>()
        val tentativas = mutableListOf<Long>()
        override suspend fun paraEnviar() = fila
        override suspend fun concluir(alvoId: String) { concluidos += alvoId }
        override suspend fun registrarTentativa(seq: Long) { tentativas += seq }
        override suspend fun marcarFalhaPermanente(alvoId: String, mensagem: String) {
            permanentes += alvoId to mensagem
        }
    }

    private class ExecutorFalso(
        override val tipos: Set<TipoOperacao>,
        val resposta: (Operacao) -> AppResult<Unit>,
    ) : ExecutorDeOperacao {
        val recebidas = mutableListOf<String>()
        override suspend fun executar(operacao: Operacao): AppResult<Unit> {
            recebidas += operacao.alvoId
            return resposta(operacao)
        }
    }

    private fun op(seq: Long, tipo: TipoOperacao, alvo: String) =
        Operacao(seq = seq, tipo = tipo, alvoId = alvo, payload = "{}")

    private val todosOsTipos = TipoOperacao.entries.toSet()

    // --- testes ---

    @Test
    fun `sucesso remove o alvo da fila`() = runTest {
        val outbox = OutboxFalso(listOf(op(1, TipoOperacao.CRIAR_TREINO, "w1")))
        val exec = ExecutorFalso(todosOsTipos) { AppResult.Success(Unit) }

        val r = ProcessadorDeOutbox(outbox, listOf(exec)).processar()

        assertEquals(ProcessadorDeOutbox.Resultado.CONCLUIDO, r)
        assertEquals(listOf("w1"), outbox.concluidos)
    }

    @Test
    fun `falha temporaria PARA a fila e nao envia o que vem depois`() = runTest {
        // O caso que justifica a regra: criar o treino falhou por rede. Se a agenda subisse
        // assim mesmo, o servidor devolveria 400 (agenda referenciando treino inexistente),
        // 400 é permanente, e a agenda do usuário seria descartada por causa de uma queda
        // momentânea de rede.
        val outbox = OutboxFalso(
            listOf(
                op(1, TipoOperacao.CRIAR_TREINO, "w1"),
                op(2, TipoOperacao.DEFINIR_AGENDA, "p1"),
            ),
        )
        val exec = ExecutorFalso(todosOsTipos) { AppResult.Failure(AppError.Connection()) }

        val r = ProcessadorDeOutbox(outbox, listOf(exec)).processar()

        assertEquals(ProcessadorDeOutbox.Resultado.TENTAR_DEPOIS, r)
        assertEquals(listOf("w1"), exec.recebidas, "a agenda NÃO podia ter sido tentada")
        assertEquals(listOf(1L), outbox.tentativas)
        assertTrue(outbox.permanentes.isEmpty(), "falta de rede não é recusa do servidor")
    }

    @Test
    fun `falha permanente marca o alvo e SEGUE para os outros`() = runTest {
        val outbox = OutboxFalso(
            listOf(
                op(1, TipoOperacao.EDITAR_TREINO, "w1"),
                op(2, TipoOperacao.EDITAR_TREINO, "w2"),
            ),
        )
        val exec = ExecutorFalso(todosOsTipos) { operacao ->
            if (operacao.alvoId == "w1") AppResult.Failure(AppError.Forbidden("Requer premium"))
            else AppResult.Success(Unit)
        }

        val r = ProcessadorDeOutbox(outbox, listOf(exec)).processar()

        assertEquals(ProcessadorDeOutbox.Resultado.CONCLUIDO, r)
        assertEquals(listOf("w1" to "Requer premium"), outbox.permanentes)
        assertEquals(listOf("w2"), outbox.concluidos, "a recusa de w1 não podia bloquear w2")
    }

    @Test
    fun `401 e temporario - token expirado nao descarta a escrita do usuario`() = runTest {
        // O refreshTokens do HttpClient renova e a próxima tentativa passa. Tratar 401 como
        // permanente jogaria fora o treino de quem só ficou uma hora sem abrir o app.
        val outbox = OutboxFalso(listOf(op(1, TipoOperacao.EDITAR_TREINO, "w1")))
        val exec = ExecutorFalso(todosOsTipos) { AppResult.Failure(AppError.Unauthorized()) }

        val r = ProcessadorDeOutbox(outbox, listOf(exec)).processar()

        assertEquals(ProcessadorDeOutbox.Resultado.TENTAR_DEPOIS, r)
        assertTrue(outbox.permanentes.isEmpty())
    }

    @Test
    fun `5xx e temporario`() = runTest {
        val outbox = OutboxFalso(listOf(op(1, TipoOperacao.EDITAR_TREINO, "w1")))
        val exec = ExecutorFalso(todosOsTipos) { AppResult.Failure(AppError.Unexpected()) }

        assertEquals(
            ProcessadorDeOutbox.Resultado.TENTAR_DEPOIS,
            ProcessadorDeOutbox(outbox, listOf(exec)).processar(),
        )
    }

    @Test
    fun `roteia cada tipo para o executor certo`() = runTest {
        val outbox = OutboxFalso(
            listOf(
                op(1, TipoOperacao.CRIAR_TREINO, "w1"),
                op(2, TipoOperacao.CRIAR_PROGRAMA, "p1"),
            ),
        )
        val deTreino = ExecutorFalso(setOf(TipoOperacao.CRIAR_TREINO)) { AppResult.Success(Unit) }
        val dePrograma = ExecutorFalso(setOf(TipoOperacao.CRIAR_PROGRAMA)) { AppResult.Success(Unit) }

        ProcessadorDeOutbox(outbox, listOf(deTreino, dePrograma)).processar()

        assertEquals(listOf("w1"), deTreino.recebidas)
        assertEquals(listOf("p1"), dePrograma.recebidas)
    }

    @Test
    fun `tipo sem executor vira falha permanente e nao trava a fila`() = runTest {
        // Bug de wiring (esqueci de registrar o executor no Koin). Sem esta saída, a operação
        // ficaria na fila para sempre e o worker rodaria de hora em hora sem nunca progredir.
        val outbox = OutboxFalso(listOf(op(1, TipoOperacao.DEFINIR_AGENDA, "p1")))
        val exec = ExecutorFalso(setOf(TipoOperacao.CRIAR_TREINO)) { AppResult.Success(Unit) }

        val r = ProcessadorDeOutbox(outbox, listOf(exec)).processar()

        assertEquals(ProcessadorDeOutbox.Resultado.CONCLUIDO, r)
        assertEquals(1, outbox.permanentes.size)
    }

    @Test
    fun `fila vazia nao chama ninguem`() = runTest {
        val exec = ExecutorFalso(todosOsTipos) { AppResult.Success(Unit) }

        val r = ProcessadorDeOutbox(OutboxFalso(emptyList()), listOf(exec)).processar()

        assertEquals(ProcessadorDeOutbox.Resultado.CONCLUIDO, r)
        assertTrue(exec.recebidas.isEmpty())
    }
}
