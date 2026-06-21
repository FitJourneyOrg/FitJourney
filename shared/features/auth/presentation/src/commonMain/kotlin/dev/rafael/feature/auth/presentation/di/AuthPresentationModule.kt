package dev.rafael.feature.auth.presentation.di

import dev.rafael.feature.auth.presentation.viewmodel.LoginViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val authPresentationModule: Module = module {
    viewModelOf(::LoginViewModel)
}