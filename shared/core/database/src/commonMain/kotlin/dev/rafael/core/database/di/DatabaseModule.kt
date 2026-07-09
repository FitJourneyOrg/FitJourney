package dev.rafael.core.database.di

import app.cash.sqldelight.db.SqlDriver
import dev.rafael.core.catalog.ExerciseLookup
import dev.rafael.core.database.ExerciseLookupImpl
import dev.rafael.core.database.FitJourneyDatabase
import org.koin.core.module.Module
import org.koin.dsl.module

expect val platformDatabaseModule: Module

val databaseModule = module {
    includes(platformDatabaseModule)
    single { FitJourneyDatabase(get<SqlDriver>()) }
    single<ExerciseLookup> { ExerciseLookupImpl(get()) }

}