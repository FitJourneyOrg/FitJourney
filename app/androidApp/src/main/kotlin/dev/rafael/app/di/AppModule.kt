package dev.rafael.app.di

import dev.rafael.app.data.session.SessionApi
import dev.rafael.app.data.stats.StatsApi
import dev.rafael.app.data.session.SessionSync
import dev.rafael.app.screens.home.HomeViewModel
import dev.rafael.app.screens.paywall.PaywallViewModel
import dev.rafael.app.screens.reveal.ProgramRevealViewModel
import dev.rafael.app.screens.session.WorkoutSessionViewModel
import dev.rafael.app.screens.splash.SplashViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val appModule = module {
    // Escopo de app (vive enquanto o processo vive). Usado p/ trabalho fire-and-forget
    // que NÃO pode morrer junto com a tela — ex.: pré-aquecer o catálogo na Splash, que
    // é destruída assim que decide a rota (popUpTo inclusive) e cancelaria o viewModelScope.
    single<CoroutineScope> { CoroutineScope(SupervisorJob() + Dispatchers.Default) }

    // Sessão de treino (Fase 5): remote + sync offline-first (outbox local).
    single { SessionApi(get()) }
    single { StatsApi(get()) }        // XP/nível/streak (ARCH #16)
    single { SessionSync(get(), get()) }

    viewModelOf(::SplashViewModel)   // injeta AuthRepository + ProfileRepository + ExerciseRepository + CoroutineScope
    viewModelOf(::HomeViewModel)     // injeta AuthRepository (logout)
    viewModelOf(::ProgramRevealViewModel)   // injeta ProgramRepository (revelação)
    viewModelOf(::PaywallViewModel)          // injeta Billing (página de assinatura)
    viewModel { (workoutId: String) -> WorkoutSessionViewModel(workoutId, get(), get(), get()) }   // execução
}
