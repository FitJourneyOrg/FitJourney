package dev.rafael.app.data.notificacoes

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import dev.rafael.contract.notificacao.NotificacaoDto
import dev.rafael.core.database.FitJourneyDatabase
import dev.rafael.core.network.TokenProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * `NotificacaoLocalDataSourceImpl` × SQLite REAL (fatia "notificações offline", 2026-10-22).
 *
 * Mesmo motivo de `ProgramLocalDataSourceTest`: a proteção que importa mora em SQL
 * (`INSERT OR REPLACE`, `WHERE uid = ?`, `WHERE readAt IS NULL`) e num `transaction { }` — um
 * fake de `NotificacaoLocalDataSource` não prova nada porque reimplementaria a mesma lógica que
 * o teste deveria desconfiar. `JdbcSqliteDriver` com `deriveSchemaFromMigrations` roda a `9.sqm`
 * real.
 */
class NotificacaoLocalDataSourceImplTest {

    private lateinit var driver: JdbcSqliteDriver
    private lateinit var ds: NotificacaoLocalDataSourceImpl
    private lateinit var clock: ClockDeTeste

    private class TokenProviderDeTeste(uid: String?) : TokenProvider {
        private val fluxo: MutableStateFlow<String?> = MutableStateFlow(uid)
        override suspend fun currentToken(): String? = "token-de-teste"
        override suspend fun currentUid(): String? = fluxo.value
        override fun uidFlow(): Flow<String?> = fluxo
        fun trocarPara(uid: String?) { fluxo.value = uid }
    }

    /** Relógio controlável: prova que `readAt` vem do `Clock` injetado, não de `Clock.System`. */
    private class ClockDeTeste(private var agora: Instant) : Clock {
        override fun now(): Instant = agora
        fun avancar(novo: Instant) { agora = novo }
    }

    private lateinit var tokenProvider: TokenProviderDeTeste

    @BeforeTest
    fun setup() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        FitJourneyDatabase.Schema.create(driver)
        val db = FitJourneyDatabase(driver)
        tokenProvider = TokenProviderDeTeste(uid = "u1")
        clock = ClockDeTeste(Instant.parse("2026-10-22T10:00:00Z"))
        ds = NotificacaoLocalDataSourceImpl(db, tokenProvider, clock)
    }

    @AfterTest
    fun tearDown() {
        driver.close()
    }

    private fun notificacao(
        id: String,
        lida: Boolean = false,
        dados: Map<String, String> = emptyMap(),
    ) = NotificacaoDto(
        id = id,
        type = "grupo_convite",
        title = "Título $id",
        body = "Corpo $id",
        data = dados,
        readAt = if (lida) "2026-10-20T00:00:00" else null,
        createdAt = "2026-10-21T00:00:00",
    )

    @Test
    fun `save grava o snapshot e ler devolve tudo, mais recente primeiro`() = runTest {
        ds.save(
            notificacoes = listOf(
                notificacao("n1").copy(createdAt = "2026-10-20T00:00:00"),
                notificacao("n2").copy(createdAt = "2026-10-21T00:00:00"),
            ),
            marcarTodasComoLidas = false,
        )

        val lidas = ds.ler()

        assertEquals(listOf("n2", "n1"), lidas.map { it.id }, "ORDER BY createdAt DESC")
    }

    @Test
    fun `save substitui o estado anterior por completo (replace, nao soma)`() = runTest {
        ds.save(listOf(notificacao("n1")), marcarTodasComoLidas = false)

        ds.save(listOf(notificacao("n2")), marcarTodasComoLidas = false)

        assertEquals(listOf("n2"), ds.ler().map { it.id }, "n1 não devia sobreviver ao segundo save")
    }

    @Test
    fun `data (Map) sobrevive ao round-trip via JSON`() = runTest {
        ds.save(
            listOf(notificacao("n1", dados = mapOf("tipo" to "amizade", "fromUserId" to "u9"))),
            marcarTodasComoLidas = false,
        )

        val lido = ds.ler().single()

        assertEquals(mapOf("tipo" to "amizade", "fromUserId" to "u9"), lido.data)
    }

    /** Caminho de falha do round-trip acima: `data` vazio não pode virar erro de parse. */
    @Test
    fun `data vazio sobrevive ao round-trip como mapa vazio`() = runTest {
        ds.save(listOf(notificacao("n1", dados = emptyMap())), marcarTodasComoLidas = false)

        assertEquals(emptyMap(), ds.ler().single().data)
    }

    @Test
    fun `save com marcarTodasComoLidas=true marca as recem-inseridas como lidas`() = runTest {
        ds.save(listOf(notificacao("n1", lida = false)), marcarTodasComoLidas = true)

        val lido = ds.ler().single()

        assertEquals(clock.now().toString(), lido.readAt)
    }

    @Test
    fun `save com marcarTodasComoLidas=false preserva readAt nulo (ainda nao lida)`() = runTest {
        ds.save(listOf(notificacao("n1", lida = false)), marcarTodasComoLidas = false)

        assertNull(ds.ler().single().readAt)
    }

    @Test
    fun `marcarTodasComoLidasLocal so toca quem estava com readAt nulo`() = runTest {
        ds.save(
            notificacoes = listOf(
                notificacao("lida-antes", lida = true),
                notificacao("nao-lida", lida = false),
            ),
            marcarTodasComoLidas = false,
        )

        clock.avancar(Instant.parse("2026-10-22T12:00:00Z"))
        ds.marcarTodasComoLidasLocal()

        val porId = ds.ler().associateBy { it.id }
        assertEquals("2026-10-20T00:00:00", porId.getValue("lida-antes").readAt, "não reescreve quem já tinha data")
        assertEquals(clock.now().toString(), porId.getValue("nao-lida").readAt)
    }

    @Test
    fun `isolamento por uid - conta B nao ve nem apaga notificacao da conta A`() = runTest {
        ds.save(listOf(notificacao("de-a")), marcarTodasComoLidas = false)

        tokenProvider.trocarPara("u2")
        ds.save(listOf(notificacao("de-b")), marcarTodasComoLidas = false)

        assertEquals(listOf("de-b"), ds.ler().map { it.id }, "u2 só vê o que é seu")

        tokenProvider.trocarPara("u1")
        assertEquals(listOf("de-a"), ds.ler().map { it.id }, "o save de u2 não pode ter apagado o de u1")
    }
}
