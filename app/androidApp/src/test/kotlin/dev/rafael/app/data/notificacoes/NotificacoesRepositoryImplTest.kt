package dev.rafael.app.data.notificacoes

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import dev.rafael.contract.notificacao.NotificacaoDto
import dev.rafael.core.database.FitJourneyDatabase
import dev.rafael.core.database.SyncStamps
import dev.rafael.core.database.outbox.AgendadorDeSync
import dev.rafael.core.database.outbox.FilaDeSaida
import dev.rafael.core.database.outbox.Operacao
import dev.rafael.core.database.outbox.Outbox
import dev.rafael.core.database.outbox.ProcessadorDeOutbox
import dev.rafael.core.database.outbox.TipoOperacao
import dev.rafael.core.result.AppError
import dev.rafael.core.result.AppResult
import dev.rafael.core.result.asFailure
import dev.rafael.core.result.asSuccess
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * `NotificacoesRepositoryImpl` com fakes escritos à mão (C2) para `NotificacoesRemota`,
 * `NotificacaoLocalDataSource` e `SyncStamps` — a proteção de SQL já está coberta em
 * [NotificacaoLocalDataSourceImplTest]; o que este teste prova é a ESCOLHA cache-vs-rede-vs-falha,
 * que é lógica pura por cima dos fakes.
 *
 * `Outbox` e `ProcessadorDeOutbox` são classes CONCRETAS (não há interface para `alvosPendentes()`
 * fora de `FilaDeSaida`), então usam SQLite real em memória — o mesmo raciocínio do
 * `NotificacaoLocalDataSourceImplTest`, só que aqui o banco é só coadjuvante da fila, não o alvo
 * do teste.
 *
 * Sem precedente de "repositório com fake" neste módulo app (`ProgramRepositoryImpl` não tem um
 * teste assim) — `NotificacaoLocalDataSource` virou interface justamente para isto.
 */
class NotificacoesRepositoryImplTest {

    private lateinit var driver: JdbcSqliteDriver
    private lateinit var outbox: Outbox
    private lateinit var processadorSemExecutor: ProcessadorDeOutbox
    private lateinit var agendador: ContadorDeAgendamentos

    @BeforeTest
    fun setup() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        FitJourneyDatabase.Schema.create(driver)
        outbox = Outbox(db = FitJourneyDatabase(driver), uidAtual = { "u1" })
        // A compactação/envio da fila tem teste próprio (CompactadorDeOutbox/ProcessadorDeOutbox);
        // aqui só precisa NUNCA ter nada a enviar, pra `listar()` não depender de rede duas vezes.
        processadorSemExecutor = ProcessadorDeOutbox(outbox = FilaVaziaDeSaida, executores = emptyList())
        agendador = ContadorDeAgendamentos()
    }

    @AfterTest
    fun tearDown() {
        driver.close()
    }

    private object FilaVaziaDeSaida : FilaDeSaida {
        override suspend fun paraEnviar(): List<Operacao> = emptyList()
        override suspend fun concluir(alvoId: String) {}
        override suspend fun registrarTentativa(seq: Long) {}
        override suspend fun marcarFalhaPermanente(alvoId: String, mensagem: String) {}
    }

    private class ContadorDeAgendamentos : AgendadorDeSync {
        var chamadas = 0
        override fun agendar() { chamadas++ }
    }

    private class FakeRemota(
        private val aoListar: suspend () -> AppResult<List<NotificacaoDto>> = { emptyList<NotificacaoDto>().asSuccess() },
    ) : NotificacoesRemota {
        override suspend fun listar(): AppResult<List<NotificacaoDto>> = aoListar()
        override suspend fun marcarComoLidas(): AppResult<Unit> = Unit.asSuccess()
        override suspend fun registrarDispositivo(token: String): AppResult<Unit> = Unit.asSuccess()
        override suspend fun darBaixaNoDispositivo(token: String): AppResult<Unit> = Unit.asSuccess()
    }

    private class FakeLocal : NotificacaoLocalDataSource {
        var salvas: List<NotificacaoDto> = emptyList()
        var ultimaChamadaMarcouTodasComoLidas: Boolean? = null
        var marcarTodasComoLidasLocalChamadas = 0

        override suspend fun save(notificacoes: List<NotificacaoDto>, marcarTodasComoLidas: Boolean) {
            salvas = notificacoes
            ultimaChamadaMarcouTodasComoLidas = marcarTodasComoLidas
        }
        override suspend fun ler(): List<NotificacaoDto> = salvas
        override suspend fun marcarTodasComoLidasLocal() { marcarTodasComoLidasLocalChamadas++ }
    }

    private class FakeStamps : SyncStamps {
        private val marcados = mutableSetOf<String>()
        override suspend fun fresco(chave: String, ttlMs: Long, escopo: SyncStamps.Escopo) = false
        override suspend fun jaSincronizou(chave: String, escopo: SyncStamps.Escopo) = chave in marcados
        override suspend fun marcar(chave: String, escopo: SyncStamps.Escopo) { marcados += chave }
        override suspend fun invalidar(chave: String, escopo: SyncStamps.Escopo) { marcados -= chave }
    }

    private fun repo(remota: FakeRemota, local: FakeLocal, stamps: FakeStamps) = NotificacoesRepositoryImpl(
        remote = remota,
        local = local,
        stamps = stamps,
        outbox = outbox,
        agendador = agendador,
        processador = processadorSemExecutor,
    )

    private val dtoDeExemplo = NotificacaoDto(id = "n1", type = "t", title = "T", body = "B", createdAt = "2026-10-22T00:00:00")

    @Test
    fun `sincronizacao com sucesso salva local, carimba e devolve o que o local tem`() = runTest {
        val local = FakeLocal()
        val stamps = FakeStamps()
        val r = repo(FakeRemota(aoListar = { listOf(dtoDeExemplo).asSuccess() }), local, stamps)

        val resultado = r.listar()

        assertTrue(resultado is AppResult.Success)
        assertEquals(listOf(dtoDeExemplo), local.salvas)
        assertTrue(stamps.jaSincronizou(SyncStamps.NOTIFICACOES))
    }

    /** Caminho de falha: sem rede e SEM sync anterior, o erro sobe -- não há cache pra servir. */
    @Test
    fun `falha de conexao sem sync anterior propaga o erro (nao ha cache)`() = runTest {
        val r = repo(FakeRemota(aoListar = { AppError.Connection().asFailure() }), FakeLocal(), FakeStamps())

        val resultado = r.listar()

        assertTrue(resultado is AppResult.Failure)
        assertTrue((resultado as AppResult.Failure).error is AppError.Connection)
    }

    /** ⭐ O caso que justifica a fatia: offline, MAS já sincronizou antes -- serve o cache. */
    @Test
    fun `falha de conexao com sync anterior serve o cache local`() = runTest {
        val local = FakeLocal().apply { salvas = listOf(dtoDeExemplo) }
        val stamps = FakeStamps().apply { marcar(SyncStamps.NOTIFICACOES) }
        val r = repo(FakeRemota(aoListar = { AppError.Connection().asFailure() }), local, stamps)

        val resultado = r.listar()

        assertTrue(resultado is AppResult.Success)
        assertEquals(listOf(dtoDeExemplo), (resultado as AppResult.Success).value)
    }

    /** Falha que NÃO é de transporte (ex. 401) nunca serve cache, mesmo com sync anterior. */
    @Test
    fun `falha que nao e de conexao propaga mesmo com sync anterior`() = runTest {
        val local = FakeLocal().apply { salvas = listOf(dtoDeExemplo) }
        val stamps = FakeStamps().apply { marcar(SyncStamps.NOTIFICACOES) }
        val r = repo(FakeRemota(aoListar = { AppError.Unauthorized().asFailure() }), local, stamps)

        val resultado = r.listar()

        assertTrue(resultado is AppResult.Failure)
        assertTrue((resultado as AppResult.Failure).error is AppError.Unauthorized)
    }

    /**
     * ⭐ A proteção que [NotificacoesRepositoryImpl.listar] existe para dar: marcar como lida
     * offline e, no PRÓXIMO `listar()`, o servidor ainda devolve as notificações como não lidas
     * (o POST ainda está na fila) -- sem isto, o replace do `save()` as "desleria" de volta.
     */
    @Test
    fun `com marcacao pendente no outbox, listar reaplica marcarTodasComoLidas apos o replace`() = runTest {
        outbox.enfileirar(TipoOperacao.MARCAR_NOTIFICACAO_LIDA, NotificacoesRepositoryImpl.ALVO_MARCAR_LIDAS)
        val local = FakeLocal()
        val r = repo(FakeRemota(aoListar = { listOf(dtoDeExemplo).asSuccess() }), local, FakeStamps())

        r.listar()

        assertEquals(true, local.ultimaChamadaMarcouTodasComoLidas)
    }

    @Test
    fun `sem marcacao pendente, listar NAO reaplica marcarTodasComoLidas`() = runTest {
        val local = FakeLocal()
        val r = repo(FakeRemota(aoListar = { listOf(dtoDeExemplo).asSuccess() }), local, FakeStamps())

        r.listar()

        assertEquals(false, local.ultimaChamadaMarcouTodasComoLidas)
    }

    @Test
    fun `marcarComoLidas grava local, enfileira o tipo certo e agenda o envio`() = runTest {
        val local = FakeLocal()
        val r = repo(FakeRemota(), local, FakeStamps())

        val resultado = r.marcarComoLidas()

        assertTrue(resultado is AppResult.Success)
        assertEquals(1, local.marcarTodasComoLidasLocalChamadas)
        assertEquals(1, agendador.chamadas)
        val pendente = outbox.paraEnviar().single()
        assertEquals(TipoOperacao.MARCAR_NOTIFICACAO_LIDA, pendente.tipo)
        assertEquals(NotificacoesRepositoryImpl.ALVO_MARCAR_LIDAS, pendente.alvoId)
    }
}
