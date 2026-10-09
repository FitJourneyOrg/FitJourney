package dev.rafael.app.data.notificacoes

import dev.rafael.contract.notificacao.NotificacaoDto
import dev.rafael.core.result.AppError
import dev.rafael.core.result.AppResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * O número do badge (F.1).
 *
 * ## Por que ISTO tem teste, sendo três linhas
 *
 * Porque o caso que importa é o de FALHA, e ele é invisível na bateria manual: um badge que
 * mostra o número certo e um badge que zerou por erro de rede parecem a mesma coisa na tela
 * quando não há notificação nenhuma. Só se percebe o defeito quando havia número e ele sumiu —
 * e aí a pessoa já não sabe que tinha algo para ver.
 */
class ContadorDeNaoLidasTest {

    private fun nota(id: String, lida: Boolean) = NotificacaoDto(
        id = id,
        type = "PEDIDO_DE_AMIZADE",
        title = "Fulano quer ser seu amigo",
        body = "Toque para ver o pedido",
        readAt = if (lida) "2026-08-29T10:00:00" else null,
        createdAt = "2026-08-29T09:00:00",
    )

    private class FakeNotificacoes(var resposta: AppResult<List<NotificacaoDto>>) : Notificacoes {
        override suspend fun listar() = resposta
        override suspend fun marcarComoLidas() = AppResult.Success(Unit)
        override suspend fun registrarDispositivo(token: String) = error("não usado")
        override suspend fun darBaixaNoDispositivo(token: String) = error("não usado")
    }

    /**
     * Uma fonte em que a PRIMEIRA pergunta fica pendurada até o teste soltar o [portao]: é como se
     * simula "requisição em andamento" sem depender de tempo real. As seguintes respondem na hora.
     */
    private class FonteComPortao(var resposta: AppResult<List<NotificacaoDto>>) : Notificacoes {
        val portao = CompletableDeferred<Unit>()
        var perguntas = 0

        override suspend fun listar(): AppResult<List<NotificacaoDto>> {
            perguntas++
            val ehAPrimeira = perguntas == 1
            val resp = resposta
            if (ehAPrimeira) portao.await()
            return resp
        }
        override suspend fun marcarComoLidas() = AppResult.Success(Unit)
        override suspend fun registrarDispositivo(token: String) = error("não usado")
        override suspend fun darBaixaNoDispositivo(token: String) = error("não usado")
    }

    @Test
    fun `conta apenas as nao lidas`() = runTest {
        val fonte = FakeNotificacoes(
            AppResult.Success(
                listOf(nota("1", lida = false), nota("2", lida = true), nota("3", lida = false)),
            ),
        )
        val contador = ContadorDeNaoLidas(fonte)

        contador.atualizar()

        assertEquals(2, contador.quantidade.value, "lida não entra no badge")
    }

    /**
     * [INVARIANTE] Falhar não zera o badge.
     *
     * Zerar diria "você não tem notificações" quando a verdade é "não consegui perguntar". É a
     * mesma regra da C.1, que não apaga o perfil já exibido ao falhar em recarregar: **erro sobre
     * dado velho é melhor que dado falso**.
     */
    @Test
    fun `falha preserva o numero que ja estava no badge`() = runTest {
        val fonte = FakeNotificacoes(AppResult.Success(listOf(nota("1", lida = false))))
        val contador = ContadorDeNaoLidas(fonte)

        contador.atualizar()
        assertEquals(1, contador.quantidade.value)

        // O ON_START da próxima tela-raiz pega a rede fora do ar.
        fonte.resposta = AppResult.Failure(AppError.Connection())
        contador.atualizar()

        assertEquals(1, contador.quantidade.value, "o badge segura o último número que ele SABE")
    }

    @Test
    fun `zerar apaga o badge sem requisicao`() = runTest {
        val fonte = FakeNotificacoes(AppResult.Success(listOf(nota("1", lida = false))))
        val contador = ContadorDeNaoLidas(fonte)

        contador.atualizar()
        contador.zerar()

        assertEquals(0, contador.quantidade.value)
    }

    // ---- B4: uma pergunta por vez ----

    @Test
    fun `chamadas sobrepostas viram uma so requisicao`() = runTest {
        val fonte = FonteComPortao(AppResult.Success(listOf(nota("1", lida = false))))
        val contador = ContadorDeNaoLidas(fonte)

        // O boot: rota nula, Splash, Home e a sessão restaurada, todos antes da rede responder.
        repeat(4) { launch { contador.atualizar() } }
        runCurrent()
        fonte.portao.complete(Unit)
        advanceUntilIdle()

        assertEquals(1, fonte.perguntas, "quatro gatilhos do boot ainda viraram mais de um GET")
        assertEquals(1, contador.quantidade.value, "quem esperou não recebeu o resultado")
    }

    @Test
    fun `depois que a pergunta termina, a proxima chamada pergunta de novo`() = runTest {
        val fonte = FonteComPortao(AppResult.Success(listOf(nota("1", lida = false))))
        val contador = ContadorDeNaoLidas(fonte)
        fonte.portao.complete(Unit)

        contador.atualizar()
        contador.atualizar()

        assertEquals(2, fonte.perguntas, "o contador passou a reaproveitar resposta velha")
    }

    /**
     * ⭐ O push NÃO se junta à pergunta em andamento: ela pode ter saído antes de a notificação
     * existir no servidor. Ele pede UMA repetição (e só uma, mesmo com vários pushes seguidos).
     */
    @Test
    fun `push durante a pergunta refaz uma vez e mostra o numero novo`() = runTest {
        val fonte = FonteComPortao(AppResult.Success(listOf(nota("1", lida = false))))
        val contador = ContadorDeNaoLidas(fonte)

        launch { contador.atualizar() }
        runCurrent()
        // O servidor ganha uma segunda notificação enquanto a primeira pergunta está no ar.
        fonte.resposta = AppResult.Success(listOf(nota("1", lida = false), nota("2", lida = false)))
        repeat(3) { launch { contador.atualizar(aposEvento = true) } }
        runCurrent()
        fonte.portao.complete(Unit)
        advanceUntilIdle()

        assertEquals(2, fonte.perguntas, "três pushes seguidos viraram mais de uma repetição")
        assertEquals(2, contador.quantidade.value, "o push foi engolido e o badge ficou velho")
    }

    /** Caminho de falha (C2): a pergunta falha e nada fica preso. */
    @Test
    fun `pergunta que falha preserva o badge e nao trava as proximas`() = runTest {
        val fonte = FonteComPortao(AppResult.Success(listOf(nota("1", lida = false))))
        val contador = ContadorDeNaoLidas(fonte)
        fonte.portao.complete(Unit)
        contador.atualizar()

        fonte.resposta = AppResult.Failure(AppError.Connection())
        contador.atualizar()
        assertEquals(1, contador.quantidade.value, "falhar zerou o badge")

        fonte.resposta = AppResult.Success(listOf(nota("1", lida = false), nota("2", lida = false)))
        contador.atualizar()
        assertEquals(2, contador.quantidade.value, "depois da falha o contador ficou preso")
    }

    /** Caminho de falha (C2): a tela some no meio da requisição. */
    @Test
    fun `pergunta cancelada no meio libera quem espera e as proximas`() = runTest {
        val fonte = FonteComPortao(AppResult.Success(listOf(nota("1", lida = false))))
        val contador = ContadorDeNaoLidas(fonte)

        val dono = launch { contador.atualizar() }
        runCurrent()
        val espera = launch { contador.atualizar() }
        runCurrent()
        dono.cancel()
        advanceUntilIdle()

        assertEquals(true, espera.isCompleted, "quem esperava ficou pendurado depois do cancelamento")

        contador.atualizar()
        assertEquals(2, fonte.perguntas, "a pergunta cancelada deixou o contador travado")
        assertEquals(1, contador.quantidade.value)
    }
}
