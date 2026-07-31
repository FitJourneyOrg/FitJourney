package dev.rafael.app.screens.splash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.rafael.app.navigation.AppRoute
import dev.rafael.core.result.AppError
import dev.rafael.core.result.AppResult
import dev.rafael.features.auth.domain.repository.AuthRepository
import dev.rafael.features.exercise.domain.repository.ExerciseRepository
import dev.rafael.features.profile.domain.repository.ProfileRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class SplashViewModel(
    private val auth: AuthRepository,
    private val profile: ProfileRepository,
    private val exercises: ExerciseRepository,
    private val appScope: CoroutineScope,
) : ViewModel() {

    private val _state = MutableStateFlow<SplashState>(SplashState.Loading)
    val state: StateFlow<SplashState> = _state.asStateFlow()

    init { decide() }

    private fun decide() {
        viewModelScope.launch {
            val token = auth.currentIdToken()
            if (token == null) {
                _state.value = SplashState.Decided(AppRoute.Login)
                return@launch
            }

            // Timeout maior: o 1º request após o boot do server é lento (JIT/pool) e
            // estourava 1,5s → caía no fallback (cache stale) → Home errado p/ cadastro novo.
            val result = withTimeoutOrNull(5000) { profile.getProfile() }
            val dest = when (result) {
                is AppResult.Success ->
                    if (result.value.onboardingCompleted) AppRoute.Home else AppRoute.Quiz
                is AppResult.Failure ->
                    if (result.error is AppError.NotFound) AppRoute.Quiz
                    else fallbackFromCache()          // <- rede falhou: usa cache
                null -> fallbackFromCache()            // <- timeout: usa cache
            }
            _state.value = SplashState.Decided(dest)

            // SÓ AGORA aquece o catálogo (fire-and-forget no appScope). Roda DEPOIS da
            // decisão de rota de propósito: se disparado antes, o GET /exercises (catálogo
            // inteiro) concorria com o getProfile na mesma HttpClient e, em server frio,
            // empurrava o getProfile além dos 5s → fallback (cache stale) → Home errado no
            // cadastro novo. No appScope o warm sobrevive à Splash ser destruída.
            warmExerciseCatalog()
        }
    }

    private suspend fun fallbackFromCache(): AppRoute =
        when (profile.cachedOnboardingCompleted()) {
            true  -> AppRoute.Home
            false -> AppRoute.Quiz
            null  -> AppRoute.Home   // device novo + offline + nunca cacheou: sem info, chuta Home
        }

    /**
     * Popula o cache local do catálogo em background; ignora falha (degrada offline).
     * Roda no appScope (não no viewModelScope): a Splash é destruída assim que decide a
     * rota, e o viewModelScope cancelaria o refresh no meio — deixando o cache vazio e o
     * WorkoutDetail mostrando "Exercício indisponível". No appScope o warm conclui.
     */
    private fun warmExerciseCatalog() {
        appScope.launch { exercises.refresh() }
    }
}