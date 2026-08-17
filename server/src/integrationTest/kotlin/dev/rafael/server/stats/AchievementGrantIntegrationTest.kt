package dev.rafael.server.stats

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import dev.rafael.core.result.AppResult
import dev.rafael.server.db.Migrations
import dev.rafael.server.features.stats.db.AchievementRepositoryImpl
import dev.rafael.server.features.user.db.UsersTable
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.testcontainers.postgresql.PostgreSQLContainer
import kotlin.uuid.Uuid

/**
 * Concessão de conquistas contra Postgres REAL (ARCH #16).
 *
 * POR QUE contra o banco: a idempotência não está no Kotlin — está na PK composta
 * `(user_id, achievement_id)` mais o `ON CONFLICT DO NOTHING`. Um fake não provaria nada.
 *
 * A avaliação roda a CADA leitura da tela de conquistas, então reconceder é o caminho comum,
 * não a exceção. Se sobrescrevesse a data, o "desbloqueado em" viraria "visto pela última vez"
 * — e a ordenação da tela, junto com qualquer notificação futura, passaria a mentir.
 *
 * Escolhi este teste, e não mais um da policy, pela lição da fatia B: os quatro defeitos
 * daquela bateria estavam todos FORA da lógica pura.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AchievementGrantIntegrationTest {

    private val postgres = PostgreSQLContainer("postgres:16-alpine")
    private lateinit var ds: HikariDataSource
    private val repo = AchievementRepositoryImpl()

    /**
     * Cada teste cria o SEU usuário.
     *
     * A primeira versão compartilhava um só, e quebrou na hora: JUnit não garante ordem, então
     * um teste que concede NIVEL_5 fazia o `assertEquals` de outro enxergar chave a mais. Com
     * container `PER_CLASS` (caro de subir), o isolamento tem de vir da CHAVE — a mesma lição
     * do uid no cliente. Relaxar a asserção para `contains` esconderia o problema em vez de
     * resolvê-lo, e um teste que depende de ordem é defeito esperando acontecer.
     */
    private fun novoUsuario(): Uuid {
        val id = Uuid.random()
        transaction {
            UsersTable.insert {
                it[UsersTable.id] = id
                it[firebaseUid] = "uid-$id"
                it[email] = "$id@teste.local"
            }
        }
        return id
    }

    @BeforeAll
    fun setup() {
        postgres.start()
        ds = HikariConfig().apply {
            jdbcUrl = postgres.jdbcUrl
            username = postgres.username
            password = postgres.password
            driverClassName = "org.postgresql.Driver"
            isAutoCommit = false
        }.let(::HikariDataSource)
        Migrations.run(ds)   // inclui V34
        Database.connect(ds)

    }

    @AfterAll
    fun teardown() {
        ds.close()
        postgres.stop()
    }

    private fun <T> ok(r: AppResult<T>): T = (r as AppResult.Success).value

    @Test
    fun `concede e le de volta`() = runBlocking {
        val usuario = novoUsuario()
        ok(repo.grant(usuario, setOf("PRIMEIRO_TREINO", "TREINOS_10")))

        val tem = ok(repo.listByUser(usuario))

        assertEquals(setOf("PRIMEIRO_TREINO", "TREINOS_10"), tem.keys)
        assertNotNull(tem["PRIMEIRO_TREINO"], "unlocked_at tem de vir preenchido pelo banco")
    }

    @Test
    fun `reconceder NAO duplica nem sobrescreve a data`() = runBlocking {
        val usuario = novoUsuario()
        ok(repo.grant(usuario, setOf("STREAK_7")))
        val dataOriginal = ok(repo.listByUser(usuario))["STREAK_7"]

        Thread.sleep(1_100)   // garante timestamp diferente se houvesse sobrescrita
        ok(repo.grant(usuario, setOf("STREAK_7")))

        val depois = ok(repo.listByUser(usuario))
        assertEquals(
            dataOriginal,
            depois["STREAK_7"],
            "a data do desbloqueio é fato histórico — reconceder não pode movê-la",
        )
        assertEquals(1, depois.keys.count { it == "STREAK_7" })
    }

    @Test
    fun `lote com uma nova e uma ja concedida grava so a nova`() = runBlocking {
        // Caso REAL da tela: o usuário já tem PRIMEIRO_TREINO e acabou de chegar em 50.
        val usuario = novoUsuario()
        ok(repo.grant(usuario, setOf("PRIMEIRO_TREINO")))

        ok(repo.grant(usuario, setOf("PRIMEIRO_TREINO", "TREINOS_50")))

        assertEquals(setOf("PRIMEIRO_TREINO", "TREINOS_50"), ok(repo.listByUser(usuario)).keys)
    }

    @Test
    fun `lote vazio nao quebra`() = runBlocking {
        // O caminho mais comum de todos: abrir a tela sem nada novo a conceder.
        val usuario = novoUsuario()
        ok(repo.grant(usuario, setOf("PRIMEIRO_TREINO")))

        ok(repo.grant(usuario, emptySet()))

        assertEquals(setOf("PRIMEIRO_TREINO"), ok(repo.listByUser(usuario)).keys)
    }

    @Test
    fun `conquista de um usuario nao vaza para outro`() = runBlocking {
        // [REGRA] tudo chaveado por usuário. A PK composta já obriga, mas o isolamento é o tipo
        // de coisa que precisa de asserção explícita — foi assim que um bug de conta cruzada
        // apareceu no cache do cliente.
        val usuario = novoUsuario()
        val outro = novoUsuario()
        ok(repo.grant(usuario, setOf("NIVEL_5")))

        assertTrue(ok(repo.listByUser(outro)).isEmpty())
    }

    @Test
    fun `id desconhecido e aceito no banco e ignorado na leitura do dominio`() = runBlocking {
        // `achievement_id` é TEXT justamente para conquista nova não exigir migration. O preço
        // é que um id removido do código pode continuar no banco: a linha fica órfã e o
        // AchievementService a ignora, em vez de derrubar a tela.
        val usuario = novoUsuario()
        ok(repo.grant(usuario, setOf("CONQUISTA_QUE_NAO_EXISTE_MAIS")))

        assertTrue("CONQUISTA_QUE_NAO_EXISTE_MAIS" in ok(repo.listByUser(usuario)))
    }
}
