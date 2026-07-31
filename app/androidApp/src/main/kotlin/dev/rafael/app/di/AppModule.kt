package dev.rafael.app.di

import dev.rafael.app.screens.home.HomeViewModel
import dev.rafael.app.screens.splash.SplashViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val appModule = module {
    // Escopo de app (vive enquanto o processo vive). Usado p/ trabalho fire-and-forget
    // que NÃO pode morrer junto com a tela — ex.: pré-aquecer o catálogo na Splash, que
    // é destruída assim que decide a rota (popUpTo inclusive) e cancelaria o viewModelScope.
    single<CoroutineScope> { CoroutineScope(SupervisorJob() + Dispatchers.Default) }

    viewModelOf(::SplashViewModel)   // injeta AuthRepository + ProfileRepository + ExerciseRepository + CoroutineScope
    viewModelOf(::HomeViewModel)     // injeta AuthRepository (logout)
}
