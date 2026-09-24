package dev.rafael.features.achievements.presentation.di

import dev.rafael.features.achievements.presentation.viewmodel.AchievementsViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val achievementsPresentationModule = module {
    viewModelOf(::AchievementsViewModel)   // conquistas offline-first
}
