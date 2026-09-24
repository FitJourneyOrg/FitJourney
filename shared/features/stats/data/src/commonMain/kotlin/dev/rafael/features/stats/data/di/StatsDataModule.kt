package dev.rafael.features.stats.data.di

import dev.rafael.features.stats.data.StatsApi
import dev.rafael.features.stats.data.StatsRepository
import dev.rafael.features.stats.domain.Stats
import org.koin.dsl.module

val statsDataModule = module {
    single { StatsApi(get()) }
    single<Stats> { StatsRepository(get(), get(), get(), get()) }
}
