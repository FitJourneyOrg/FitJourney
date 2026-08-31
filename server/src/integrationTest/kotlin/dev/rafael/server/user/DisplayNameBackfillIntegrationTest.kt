package dev.rafael.server.user

import dev.rafael.server.CodigoDeTeste
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import dev.rafael.server.features.user.services.DisplayNamePolicy
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.testcontainers.postgresql.PostgreSQLContainer
import javax.sql.DataSource
import kotlin.uuid.Uuid

/**
 * O BACKFILL da V35 contra Postgres real (ARCH #33, fatia A.0).
 *
 * POR QUE este teste existe: a regra do nome inicial está escrita DUAS vezes — em SQL, no
 * backfill da V35, e em Kotlin, no `DisplayNamePolicy.inicial()`. Duas implementações da mesma
 * regra divergem em silêncio: quem foi migrado ganharia um nome, quem entrou depois ganharia
 * outro, e ninguém perceberia até dois usuários aparecerem lado a lado num ranking.
 *
 * Um teste puro não pegaria isso — a lição da fatia B do outbox foi exatamente essa: os quatro
 * defeitos daquela bateria estavam todos FORA da lógica pura.
 *
 * A mecânica: migra só até a V34, insere usuários como eles existiam ANTES da coluna, e então
 * roda a V35. É a única forma de exercitar o backfill; depois que ele roda uma vez, não roda
 * mais.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DisplayNameBackfillIntegrationTest {

    private val postgres = PostgreSQLContainer("postgres:16-alpine")
    private lateinit var ds: HikariDataSource

    /** Usuários "legados": criados quando `users` ainda não tinha `display_name`. */
    private val comEmail = Uuid.random()
    private val semEmail = Uuid.random()
    private val emailCurto = Uuid.random()
    private val emailLongo = Uuid.random()
    private val localLongo = "a".repeat(50)

    @BeforeAll
    fun setup() {
        postgres.start()
        ds = HikariConfig().apply {
            jdbcUrl = postgres.jdbcUrl
            username = postgres.username
            password = postgres.password
            driverClassName = "org.postgresql.Driver"
            isAutoCommit = true
        }.let(::HikariDataSource)

        migrarAte("34")

        // SQL cru de propósito: `UsersTable` já conhece a coluna nova, e usá-lo aqui escreveria
        // um `display_name` que o backfill não teria o que preencher.
        ds.connection.use { c ->
            c.createStatement().use { s ->
                s.executeUpdate(
                    """
                    INSERT INTO users (id, firebase_uid, email) VALUES
                      ('$comEmail',    'uid-1', 'rafel0017@gmail.com'),
                      ('$semEmail',    'uid-2', NULL),
                      ('$emailCurto',  'uid-3', 'a@x.com'),
                      ('$emailLongo',  'uid-4', '$localLongo@x.com')
                    """.trimIndent(),
                )
            }
        }

        migrarAte(null)   // aplica a V35 — o backfill roda AQUI
    }

    @AfterAll
    fun teardown() {
        ds.close()
        postgres.stop()
    }

    private fun migrarAte(versao: String?) {
        Flyway.configure()
            .dataSource(ds as DataSource)
            .locations("classpath:db/migration")
            .apply { if (versao != null) target(org.flywaydb.core.api.MigrationVersion.fromVersion(versao)) }
            .load()
            .migrate()
    }

    private fun nomeDe(id: Uuid): String =
        ds.connection.use { c ->
            c.createStatement().use { s ->
                s.executeQuery("SELECT display_name FROM users WHERE id = '$id'").use { rs ->
                    rs.next()
                    rs.getString(1)
                }
            }
        }

    @Test
    fun `backfill usa a parte local do e-mail`() {
        assertEquals("rafel0017", nomeDe(comEmail))
    }

    @Test
    fun `backfill cai no fallback quando nao ha e-mail`() {
        assertEquals("Atleta-" + semEmail.toString().replace("-", "").take(6), nomeDe(semEmail))
    }

    @Test
    fun `backfill cai no fallback quando a parte local e curta demais`() {
        // "a@x.com" daria um caractere só, e o CHECK da própria V35 recusaria a linha.
        assertEquals("Atleta-" + emailCurto.toString().replace("-", "").take(6), nomeDe(emailCurto))
    }

    @Test
    fun `backfill trunca no limite da coluna`() {
        assertEquals(DisplayNamePolicy.MAX, nomeDe(emailLongo).length)
    }

    @Test
    fun `SQL e Kotlin concordam em todos os casos`() {
        // A asserção que dá sentido às outras quatro: o nome que a migration escreveu é
        // EXATAMENTE o que o Kotlin escreveria para o mesmo usuário.
        listOf(
            comEmail to "rafel0017@gmail.com",
            semEmail to null,
            emailCurto to "a@x.com",
            emailLongo to "$localLongo@x.com",
        ).forEach { (id, email) ->
            assertEquals(
                DisplayNamePolicy.inicial(email, id),
                nomeDe(id),
                "backfill da V35 divergiu de DisplayNamePolicy.inicial() para $email",
            )
        }
    }

    @Test
    fun `o CHECK recusa nome curto demais`() {
        // O `code` entra preenchido e VÁLIDO de propósito: este teste afirma que a recusa vem do
        // CHECK do display_name, e uma segunda violação na mesma linha mudaria o motivo do erro.
        // Foi o que aconteceu quando a V40 tornou `users.code` NOT NULL — o teste passou a falhar
        // porque o banco recusava ANTES de chegar no nome, e a asserção da mensagem pegou.
        // É o teste fazendo exatamente o que devia: **recusar por outro motivo não é passar**.
        val id = Uuid.random()
        val erro = runCatching {
            ds.connection.use { c ->
                c.createStatement().use { s ->
                    s.executeUpdate(
                        "INSERT INTO users (id, firebase_uid, email, display_name, code) " +
                            "VALUES ('$id', 'uid-check', NULL, 'R', '${CodigoDeTeste.de(id)}')",
                    )
                }
            }
        }.exceptionOrNull()

        assertTrue(erro != null, "o banco tem de recusar nome de 1 caractere, não só a UI")
        assertTrue(
            erro!!.message?.contains("users_display_name_len") == true,
            "recusa tem de vir do CHECK nomeado, e não de outra violação: ${erro.message}",
        )
    }
}
