package dev.rafael.core.database.di

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import dev.rafael.core.database.FitJourneyDatabase
import org.koin.core.module.Module
import org.koin.dsl.module

actual val platformDatabaseModule: Module = module {
    single<SqlDriver> {
        // Nome estável: migrations .sqm cuidam da evolução (ver migrations/LEIA-ME.md).
        NativeSqliteDriver(FitJourneyDatabase.Schema, "fitjourney.db")
    }
}