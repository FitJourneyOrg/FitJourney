package dev.rafael.core.database.di

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import dev.rafael.core.database.FitJourneyDatabase
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

actual val platformDatabaseModule: Module = module {
    single<SqlDriver> {
        // _v4: + kv_cache (leitura offline de programas/treinos). Sem .sqm, bumpar recria o DB.
        AndroidSqliteDriver(FitJourneyDatabase.Schema, androidContext(), "fitjourney_v4.db")
    }
}