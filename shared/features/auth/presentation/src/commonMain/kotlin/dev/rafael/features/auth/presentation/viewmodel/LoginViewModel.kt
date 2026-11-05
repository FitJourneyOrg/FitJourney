package dev.rafael.features.auth.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.rafael.core.result.AppResult
import dev.rafael.features.auth.domain.repository.AuthRepository
import dev.rafael.features.auth.presentation.state.LoginEvent
import dev.rafael.features.auth.presentation.state.LoginState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class LoginViewModel(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(LoginState())
    val state: StateFlow<LoginState> = _state.asStateFlow()

    fun onEvent(event: LoginEvent) {
        when (event) {
            is LoginEvent.EmailChanged -> _state.update { it.copy(email = event.value, error = null) }
            is LoginEvent.PasswordChanged -> _state.update { it.copy(password = event.value, error = null) }
            is LoginEvent.SubmitLogin -> submit(signUp = false)
            is LoginEvent.SubmitSignUp -> submit(signUp = true)
        }
    }

    private fun submit(signUp: Boolean) {
        val current = _state.value
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            // 1) Firebase: login ou cadastro
            val authResult = if (signUp)
                authRepository.signUp(current.email, current.password)
            else
                authRepository.signIn(current.email, current.password)
            when (authResult) {
                is AppResult.Failure ->
                    _state.update { it.copy(isLoading = false, error = authResult.error) }
                is AppResult.Success ->
                    // Entra com a sessão do Firebase, sem esperar confirmação do backend (login-sem-backend,
                    // 2026-11-05): `uid` já está em mãos, e esperar o `/me` travava o login inteiro quando o
                    // backend estava fora, mesmo com credencial válida. A Splash (`prepararAntesDaHome` ->
                    // `me.sincronizar()`) já sincroniza o resto depois, de forma resiliente a essa falha --
                    // ela é quem decide se a Home abre completa ou incompleta, não o login.
                    _state.update { it.copy(isLoading = false, loggedInUserId = authResult.value.uid) }
            }
        }
    }
}