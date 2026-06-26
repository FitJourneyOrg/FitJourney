package dev.rafael.features.profile.presentation.di

import dev.rafael.features.profile.presentation.viewmodel.QuizViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.core.module.Module
import org.koin.dsl.module

val profilePresentationModule: Module = module {
    viewModelOf(::QuizViewModel)
}