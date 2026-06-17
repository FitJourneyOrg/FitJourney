package dev.rafael.server.db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.server.config.ApplicationConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import javax.sql.DataSource

object DatabaseFactory {
    @Volatile private var dataSource: HikariDataSource? = null

    fun init(config: ApplicationConfig) {
        val ds = HikariConfig().apply {
            jdbcUrl = config.property("db.jdbcUrl").getString()
            username = config.property("db.username").getString()
            password = config.property("db.password").getString()
            driverClassName = config.property("db.driver").getString()
            maximumPoolSize = config.property("db.maxPoolSize").getString().toInt()
            isAutoCommit = false
            connectionTimeout = 2_000
            validate()
        }.let(::HikariDataSource)
        dataSource = ds

        migrate(ds)              // 1) aplica migrations
        Database.connect(ds)     // 2) só então conecta o Exposed
    }

    private fun migrate(ds: DataSource) {
        Flyway.configure()
            .dataSource(ds)
            .locations("classpath:db/migration")
            .load()
            .migrate()
    }

    /** Ping leve p/ o /health. Transação é bloqueante -> IO. */
    suspend fun isHealthy(): Boolean = withContext(Dispatchers.IO) {
        runCatching { transaction { exec("SELECT 1") } }.isSuccess
    }

    fun close() {
        dataSource?.close()
        dataSource = null
    }
}