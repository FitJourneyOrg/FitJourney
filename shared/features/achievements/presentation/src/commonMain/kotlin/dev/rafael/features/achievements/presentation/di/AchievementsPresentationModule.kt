package dev.rafael.features.achievements.presentation.di

import dev.rafael.features.achievements.presentation.viewmodel.AchievementsViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val achievementsPresentationModule = module {
    // `destaque`: id da conquista a celebrar (G.6). Mesmo padrão de parametersOf usado por
    // exercise/session — `viewModelOf` não serve mais porque o construtor ganhou um parâmetro
    // que não vem do grafo Koin.
    viewModel { (destaque: String?) -> AchievementsViewModel(get(), destaque) }
}
