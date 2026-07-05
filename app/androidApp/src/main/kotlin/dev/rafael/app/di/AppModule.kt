package dev.rafael.app.di

import dev.rafael.app.screens.splash.SplashViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val appModule = module {
    viewModelOf(::SplashViewModel)   // injeta AuthRepository + ProfileRepository (já bindados)
}