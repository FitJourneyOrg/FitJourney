package dev.rafael.server.features.notificacao.services

import dev.rafael.contract.i18n.Idioma
import dev.rafael.core.result.AppError
import dev.rafael.core.result.AppResult
import dev.rafael.core.result.asFailure
import dev.rafael.core.result.asSuccess
import dev.rafael.server.error.FalhasFake
import dev.rafael.server.features.notificacao.db.NotificationRepository
import dev.rafael.server.features.notificacao.models.Notificacao
import dev.rafael.server.features.user.db.UserRepository
import dev.rafael.server.features.user.models.User
import dev.rafael.server.features.user.services.UserService
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** C9: `avisar` nunca falha para o chamador, mas cada falha engolida precisa ficar registrada. */
class NotificacaoServiceTest {

    private val falhas = FalhasFake()
    private val erro = AppError.Validation("db fora", code = "X")

    private class Notificadores : Notificador {
        val enviados = mutableListOf<Uuid>()
        override suspend fun notificar(destinatario: Uuid, aviso: AvisoRenderizado) {
            enviados += destinatario
        }
    }

    private class Repo(private val criar: AppResult<Unit>, private val purga: AppResult<Int> = 0.asSuccess()) :
        NotificationRepository {
        override suspend fun criar(n: Notificacao) = criar
        override suspend fun doUsuario(userId: Uuid, limite: Int): AppResult<List<Notificacao>> =
            emptyList<Notificacao>().asSuccess()
        override suspend fun naoLidas(userId: Uuid) = 0.asSuccess()
        override suspend fun marcarTodasComoLidas(userId: Uuid, quando: LocalDateTime) = 0.asSuccess()
        override suspend fun purgar(anterioresA: LocalDateTime) = purga
    }

    /** Usuário que existe ou leitura que falha — só `findById` importa aqui. */
    private class Usuarios(private val leitura: AppResult<User?>) : UserRepository {
        override suspend fun findByFirebaseUid(firebaseUid: String) = leitura
        override suspend fun findById(userId: Uuid) = leitura
        override suspend fun findByCode(code: String) = leitura
        override suspend fun updateCode(userId: Uuid, code: String) = leitura
        override suspend fun create(id: Uuid, firebaseUid: String, email: String?, displayName: String, code: String): AppResult<User> =
            AppError.Validation("n/a", code = "X").asFailure()
        override suspend fun setPremium(userId: Uuid, premium: Boolean) = leitura
        override suspend fun setActiveProgram(userId: Uuid, programId: Uuid?) = leitura
        override suspend fun updateDisplayName(userId: Uuid, displayName: String) = leitura
        override suspend fun updateIdioma(userId: Uuid, idioma: Idioma) = leitura
    }

    private fun servico(repo: Repo, usuarios: Usuarios, notificador: Notificador) =
        NotificacaoService(UserService(usuarios), repo, notificador, falhas)

    private val aviso = Aviso.conquistaDesbloqueada("PRIMEIRO_TREINO")

    @Test
    fun `gravar falhou - registra e ainda tenta o push`() = runBlocking {
        val notificador = Notificadores()
        val s = servico(Repo(erro.asFailure()), Usuarios(null.asSuccess()), notificador)
        val destino = Uuid.random()

        s.avisar(destino, aviso)

        assertEquals(listOf(destino), notificador.enviados)
        assertEquals(listOf<Any?>(erro), falhas.registradas.map { it.second })
    }

    @Test
    fun `ler o idioma falhou - registra e avisa mesmo assim`() = runBlocking {
        val notificador = Notificadores()
        val s = servico(Repo(Unit.asSuccess()), Usuarios(erro.asFailure()), notificador)

        s.avisar(Uuid.random(), aviso)

        assertEquals(1, notificador.enviados.size)
        assertEquals(listOf<Any?>(erro), falhas.registradas.map { it.second })
    }

    @Test
    fun `purga que falha devolve zero e registra`() = runBlocking {
        val s = servico(Repo(Unit.asSuccess(), purga = erro.asFailure()), Usuarios(null.asSuccess()), Notificadores())

        assertEquals(0, s.purgar())
        assertEquals(1, falhas.registradas.size)
    }

    @Test
    fun `caminho feliz nao registra falha`() = runBlocking {
        val s = servico(Repo(Unit.asSuccess()), Usuarios(null.asSuccess()), Notificadores())

        s.avisar(Uuid.random(), aviso)

        assertTrue(falhas.registradas.isEmpty())
    }
}
