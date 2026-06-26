package dev.rafael.features.auth.presentation.di

import dev.rafael.features.auth.presentation.viewmodel.LoginViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val authPresentationModule: Module = module {
    viewModelOf(::LoginViewModel)
}