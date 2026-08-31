package dev.rafael.app.data.notificacoes

import dev.rafael.contract.notificacao.NotificacaoDto
import dev.rafael.core.result.AppError
import dev.rafael.core.result.AppResult
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
}
