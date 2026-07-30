package dev.rafael.core.database.di

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import dev.rafael.core.database.FitJourneyDatabase
import org.koin.core.module.Module
import org.koin.dsl.module

actual val platformDatabaseModule: Module = module {
    single<SqlDriver> {
        NativeSqliteDriver(FitJourneyDatabase.Schema, "fitjourney_v2.db")   // _v2: schema do cache mudou (uid)
    }
}