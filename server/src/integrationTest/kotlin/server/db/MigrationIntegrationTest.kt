package dev.rafael.server.db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.testcontainers.postgresql.PostgreSQLContainer

/**
 * Smoke test da migration contra um Postgres REAL (Testcontainers), não o H2.
 * Garante que V1__create_users.sql aplica no mesmo Postgres 16 de produção,
 * via o MESMO Flyway que o boot usa (Migrations.run).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MigrationIntegrationTest {

    private val postgres = PostgreSQLContainer("postgres:16-alpine")
    private lateinit var ds: HikariDataSource

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

        // o mesmo Flyway do boot
        Migrations.run(ds)
    }

    @AfterAll
    fun teardown() {
        ds.close()
        postgres.stop()
    }

    @Test
    fun `migration cria a tabela users com as colunas esperadas`() {
        ds.connection.use { conn ->
            val cols = mutableMapOf<String, String>()
            conn.metaData.getColumns(null, null, "users", null).use { rs ->
                while (rs.next()) {
                    cols[rs.getString("COLUMN_NAME")] = rs.getString("TYPE_NAME")
                }
            }
            assertTrue(cols.containsKey("id"), "coluna id deve existir")
            assertTrue(cols.containsKey("firebase_uid"), "coluna firebase_uid deve existir")
            assertTrue(cols.containsKey("email"), "coluna email deve existir")
            assertTrue(cols.containsKey("created_at"), "coluna created_at deve existir")
        }
    }

    @Test
    fun `firebase_uid tem indice unico`() {
        ds.connection.use { conn ->
            var hasUnique = false
            conn.metaData.getIndexInfo(null, null, "users", true, false).use { rs ->
                while (rs.next()) {
                    if (rs.getString("COLUMN_NAME") == "firebase_uid") hasUnique = true
                }
            }
            assertTrue(hasUnique, "firebase_uid deve ter indice unico")
        }
    }

    @Test
    fun `flyway registra a versao 1 aplicada`() {
        ds.connection.use { conn ->
            conn.createStatement().use { st ->
                st.executeQuery("SELECT COUNT(*) FROM flyway_schema_history WHERE success = true").use { rs ->
                    rs.next()
                    assertTrue(rs.getInt(1) >= 1, "deve haver ao menos 1 migration aplicada com sucesso")
                }
            }
        }
    }
}