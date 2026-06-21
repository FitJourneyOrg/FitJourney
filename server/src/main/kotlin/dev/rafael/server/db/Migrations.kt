package dev.rafael.server.db

import org.flywaydb.core.Flyway
import javax.sql.DataSource

/** Config única do Flyway — usada no boot (DatabaseFactory) e nos testes de integração. */
object Migrations {
    fun run(ds: DataSource) {
        Flyway.configure()
            .dataSource(ds)
            .locations("classpath:db/migration")
            .load()
            .migrate()
    }
}