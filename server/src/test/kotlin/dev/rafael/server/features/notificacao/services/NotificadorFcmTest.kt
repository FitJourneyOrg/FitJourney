package dev.rafael.server.features.notificacao.services

import dev.rafael.core.result.AppError
import dev.rafael.core.result.AppResult
import dev.rafael.core.result.asFailure
import dev.rafael.core.result.asSuccess
import dev.rafael.server.error.FalhasFake
import dev.rafael.server.features.notificacao.db.DeviceTokenRepository
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** C9: o que o [NotificadorFcm] engole tem que ficar registrado, e nunca pode propagar. */
class NotificadorFcmTest {

    private val falhas = FalhasFake()
    private val aviso = AvisoRenderizado("t", "titulo", "corpo", emptyMap())

    private class Tokens(private val leitura: AppResult<List<String>>) : DeviceTokenRepository {
        override suspend fun registrar(token: String, userId: Uuid, quando: LocalDateTime) = Unit.asSuccess()
        override suspend fun doUsuario(userId: Uuid) = leitura
        override suspend fun apagar(tokens: List<String>) = Unit.asSuccess()
    }

    @Test
    fun `falha ao ler tokens e registrada e nao propaga`() = runBlocking {
        val erro = AppError.Validation("db fora", code = "X")
        val n = NotificadorFcm(Tokens(erro.asFailure()), falhas) { error("fcm não deveria ser usado") }

        n.notificar(Uuid.random(), aviso)

        assertEquals(1, falhas.registradas.size)
        assertEquals(erro, falhas.registradas.single().second)
    }

    @Test
    fun `push que explode inteiro e registrado com a excecao e nao propaga`() = runBlocking {
        val boom = IllegalStateException("google fora")
        val n = NotificadorFcm(Tokens(listOf("tok").asSuccess()), falhas) { throw boom }

        n.notificar(Uuid.random(), aviso)

        // Não compara a instância: o coroutines recria a exceção ao atravessar o withContext.
        val registrado = falhas.registradas.single().second
        assertTrue(registrado is IllegalStateException)
        assertEquals(boom.message, (registrado as Throwable).message)
    }

    @Test
    fun `sem aparelho registrado nao e falha`() = runBlocking {
        val n = NotificadorFcm(Tokens(emptyList<String>().asSuccess()), falhas) { error("não deveria enviar") }

        n.notificar(Uuid.random(), aviso)

        assertTrue(falhas.registradas.isEmpty())
    }
}
