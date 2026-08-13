package dev.rafael.core.database.di

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import dev.rafael.core.database.FitJourneyDatabase
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

actual val platformDatabaseModule: Module = module {
    single<SqlDriver> {
        // NOME ESTÁVEL (sem sufixo de versão): o banco agora guarda sessões pendentes — dado que
        // só existe aqui. Evoluir o schema é papel das migrations .sqm; bumpar o nome apagaria
        // treino não sincronizado. Ver src/commonMain/sqldelight/migrations/LEIA-ME.md.
        AndroidSqliteDriver(FitJourneyDatabase.Schema, androidContext(), "fitjourney.db")
    }
}