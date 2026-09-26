package dev.rafael.features.achievements.data.di

import dev.rafael.features.achievements.data.AchievementsApi
import dev.rafael.features.achievements.data.AchievementsRepository
import dev.rafael.features.achievements.domain.Achievements
import org.koin.dsl.module

val achievementsDataModule = module {
    single { AchievementsApi(get()) }
    single<Achievements> { AchievementsRepository(get(), get(), get(), get()) }
}
