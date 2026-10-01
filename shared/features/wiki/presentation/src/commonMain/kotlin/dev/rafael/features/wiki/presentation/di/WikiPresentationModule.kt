package dev.rafael.features.wiki.presentation.di

import dev.rafael.features.wiki.presentation.viewmodel.WikiArticleViewModel
import dev.rafael.features.wiki.presentation.viewmodel.WikiViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val wikiPresentationModule: Module = module {
    viewModelOf(::WikiViewModel)
    // Mesmo padrão do ExerciseDetailViewModel: o slug vem da rota, o repositório do grafo.
    viewModel { (slug: String) -> WikiArticleViewModel(slug, get()) }
}
