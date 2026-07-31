package dev.rafael.core.database.di

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import dev.rafael.core.database.FitJourneyDatabase
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

actual val platformDatabaseModule: Module = module {
    single<SqlDriver> {
        // _v2: schema do cache mudou (uid no onboarding_cache). Sem .sqm, bumpar o nome
        // recria o cache fresco (é descartável — repopula do server/catálogo).
        AndroidSqliteDriver(FitJourneyDatabase.Schema, androidContext(), "fitjourney_v2.db")
    }
}