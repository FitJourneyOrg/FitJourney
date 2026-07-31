package dev.rafael.app.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.rafael.features.auth.domain.repository.AuthRepository
import dev.rafael.features.profile.domain.repository.ProfileRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * VM do hub (Home). Por ora só cuida do logout: signOut do Firebase (o token some →
 * currentIdToken() vira null → a Splash re-decide pro Login). loggedOut é evento one-shot
 * (a tela navega e consome).
 */
class HomeViewModel(
    private val auth: AuthRepository,
    private val profile: ProfileRepository,
) : ViewModel() {

    private val _loggedOut = MutableStateFlow(false)
    val loggedOut: StateFlow<Boolean> = _loggedOut.asStateFlow()

    fun logout() {
        viewModelScope.launch {
            profile.clearOnboardingCache()   // evita o próximo cadastro herdar o 'true' e cair na Home
            auth.signOut()                   // limpa a sessão + invalida o token cacheado do Ktor
            _loggedOut.value = true
        }
    }
}
