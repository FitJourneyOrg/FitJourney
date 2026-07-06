package dev.rafael.app.screens.splash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.rafael.app.navigation.AppRoute
import dev.rafael.core.result.AppError
import dev.rafael.core.result.AppResult
import dev.rafael.features.auth.domain.repository.AuthRepository
import dev.rafael.features.profile.domain.repository.ProfileRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class SplashViewModel(
    private val auth: AuthRepository,
    private val profile: ProfileRepository,
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
            val result = withTimeoutOrNull(1500) { profile.getProfile() }
            val dest = when (result) {
                is AppResult.Success ->
                    if (result.value.onboardingCompleted) AppRoute.Home else AppRoute.Quiz
                is AppResult.Failure ->
                    if (result.error is AppError.NotFound) AppRoute.Quiz
                    else fallbackFromCache()          // <- rede falhou: usa cache
                null -> fallbackFromCache()            // <- timeout: usa cache
            }
            _state.value = SplashState.Decided(dest)
        }
    }

    private suspend fun fallbackFromCache(): AppRoute =
        when (profile.cachedOnboardingCompleted()) {
            true  -> AppRoute.Home
            false -> AppRoute.Quiz
            null  -> AppRoute.Home   // device novo + offline + nunca cacheou: sem info, chuta Home
        }
}