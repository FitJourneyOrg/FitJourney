package dev.rafael.features.program.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import dev.rafael.contract.program.ProgramDto
import dev.rafael.contract.workout.WorkoutOrigin
import dev.rafael.core.database.FitJourneyDatabase
import dev.rafael.core.network.TokenProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * `save()` × `alvosPendentes()` contra SQLite REAL (P0.3, 2026-09-22, débito histórico).
 *
 * ## Por que este teste existe
 *
 * A dívida original: "único caminho onde uma regressão apaga dado do usuário sem erro visível".
 * `save()` recebe o snapshot COMPLETO do servidor e faz limpar + regravar tudo -- é assim que um
 * programa excluído em outro aparelho some daqui. O problema é o programa criado OFFLINE: o
 * servidor não sabe que ele existe, e um `save()` ingênuo o apagaria antes de ele sincronizar. O
 * parâmetro `pendentes` (ids na fila do outbox) é a única coisa que impede isso, em DOIS pontos
 * do método -- a limpeza (`limparXExceto`) e o re-insert (`if (id in pendentes) return@forEach`).
 * Quebrar qualquer um dos dois não dá erro de compilação nem exceção: dá sumiço silencioso de
 * dado do usuário, ou a tela "voltando no tempo" sozinha. Sem teste, ninguém percebe até alguém
 * reclamar que o treino sumiu.
 *
 * ## Por que contra SQLite real, e não um fake de `FitJourneyDatabase`
 *
 * A proteção mora em SQL (`limparProgramasExceto`/`limparAgendaExceto`/`limparTreinosExceto`,
 * ver `program.sq`/`workout.sq`) e em uma transação (`qPrograma.transaction { }`). Um fake de
 * `ProgramLocalDataSource` não prova nada porque reimplementaria a mesma lógica que o teste
 * deveria desconfiar; teria de ser um fake do BANCO. `JdbcSqliteDriver` com
 * `deriveSchemaFromMigrations` roda as `.sqm` REAIS (mesmo schema do app), então isto também é
 * uma guarda indireta sobre a `4.sqm` (fatia "rationale derivado") continuar aplicável do zero.
 * É JVM puro (JDBC) -- por isso vive em `androidHostTest`, não em `commonTest`: não resolve nos
 * targets iOS (ver comentário no `build.gradle.kts` deste módulo).
 */
class ProgramLocalDataSourceTest {

    private lateinit var driver: JdbcSqliteDriver
    private lateinit var ds: ProgramLocalDataSource

    private class TokenProviderDeTeste(uid: String) : TokenProvider {
        private val fluxo: StateFlow<String?> = MutableStateFlow(uid)
        override suspend fun currentToken(): String? = "token-de-teste"
        override suspend fun currentUid(): String? = fluxo.value
        override fun uidFlow(): StateFlow<String?> = fluxo
    }

    @BeforeTest
    fun setup() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        FitJourneyDatabase.Schema.create(driver)
        val db = FitJourneyDatabase(driver)
        ds = ProgramLocalDataSource(db, TokenProviderDeTeste(uid = "u1"))
    }

    @AfterTest
    fun tearDown() {
        driver.close()
    }

    private fun programaOffline(id: String, nome: String) = ProgramDto(
        id = id,
        name = nome,
        origin = WorkoutOrigin.MANUAL,
        daysPerWeek = 3,
    )

    /**
     * ⚠️ CAMINHO PERIGOSO, documentado de propósito: `save()` SEM `pendentes` apaga o que o
     * servidor não devolveu -- correto quando o programa foi excluído em outro aparelho, errado
     * quando é o outbox que ainda não subiu. Este teste é o contraste que dá sentido aos dois
     * seguintes: prova que a proteção NÃO é automática, é o parâmetro `pendentes` que decide.
     */
    @Test
    fun `sem pendentes, save apaga o que o servidor nao devolveu`() = runTest {
        ds.criarPrograma(programaOffline(id = "p1", nome = "Criado offline"))

        ds.save(programas = emptyList(), pendentes = emptySet())

        assertNull(ds.lerPrograma("p1"), "sem pendentes, o snapshot vazio do servidor devia apagar")
    }

    /** ⭐ A proteção na LIMPEZA -- o caso que a dívida original descreve. */
    @Test
    fun `com pendente, save NAO apaga o programa criado offline`() = runTest {
        ds.criarPrograma(programaOffline(id = "p1", nome = "Criado offline"))

        ds.save(programas = emptyList(), pendentes = setOf("p1"))

        assertEquals("Criado offline", ds.lerPrograma("p1")?.name, "pendente não pode sumir da limpeza")
    }

    /**
     * ⭐ A proteção no RE-INSERT -- sem ela, o servidor (que ainda não sabe da edição) reescreve
     * por cima e a tela "volta no tempo" sozinha.
     */
    @Test
    fun `com pendente, save NAO sobrescreve a edicao local com o dado antigo do servidor`() = runTest {
        ds.criarPrograma(programaOffline(id = "p1", nome = "Nome editado offline"))

        val versaoAntigaDoServidor = ProgramDto(
            id = "p1",
            name = "Nome antigo (servidor ainda não sabe da edição)",
            origin = WorkoutOrigin.MANUAL,
            daysPerWeek = 3,
        )
        ds.save(programas = listOf(versaoAntigaDoServidor), pendentes = setOf("p1"))

        assertEquals(
            "Nome editado offline",
            ds.lerPrograma("p1")?.name,
            "pendente não pode ser sobrescrito pela versão desatualizada do servidor",
        )
    }

    /**
     * ⭐ Débito "Round-trip de `durationWeeks`/`startedAt` sem asserção dedicada" (debitos.md,
     * destino TEST) — lacuna de C2 num campo que já é lido/escrito em produção (ARCH #22).
     *
     * Por que isto merece teste PRÓPRIO e não só confiar no `ProgramMapperTest`: o mapper prova
     * DTO↔domínio, mas os dois lados aqui atravessam o SQLDelight de verdade, que converte
     * `durationWeeks` (`Int` no DTO) para `INTEGER` via `Long` na query gerada (`.toLong()` na
     * escrita, `.toInt()` na leitura — ver `criarPrograma`/`lerPrograma`). Um `Int` que estoura
     * `Int.MAX_VALUE` nesse caminho quebraria só em runtime contra o banco real, não no mapper.
     */
    @Test
    fun `durationWeeks e startedAt sobrevivem ao round-trip local (criarPrograma - lerPrograma)`() = runTest {
        val dto = programaOffline(id = "p3", nome = "Com janela").copy(
            durationWeeks = 12,
            startedAt = "2026-09-01T00:00:00",
        )

        ds.criarPrograma(dto)
        val lido = ds.lerPrograma("p3")

        assertEquals(12, lido?.durationWeeks, "durationWeeks não pode se perder no Int->Long->Int do SQLDelight")
        assertEquals("2026-09-01T00:00:00", lido?.startedAt)
    }

    /** Caminho de falha do débito acima: `startedAt` nulo (programa cuja janela ainda não começou)
     *  não pode virar string vazia nem `"null"` no SQLite — tem que continuar nulo de verdade. */
    @Test
    fun `startedAt nulo (janela ainda nao iniciada) sobrevive ao round-trip como null`() = runTest {
        val dto = programaOffline(id = "p4", nome = "Sem janela").copy(startedAt = null)

        ds.criarPrograma(dto)

        assertNull(ds.lerPrograma("p4")?.startedAt)
    }
}
