package dev.rafael.features.auth.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.rafael.core.result.AppResult
import dev.rafael.features.auth.domain.repository.AuthRepository
import dev.rafael.features.auth.presentation.state.LoginEvent
import dev.rafael.features.auth.presentation.state.LoginState
import dev.rafael.features.auth.presentation.toMessage
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
            println()
            when (authResult) {
                is AppResult.Failure -> {
                    _state.update { it.copy(isLoading = false, error = authResult.error.toMessage()) }
                    return@launch
                }
                is AppResult.Success -> {
                    // 2) Backend: valida a sessão no /me (Firebase autentica, backend autoriza)
                    when (val me = authRepository.fetchMe()) {
                        is AppResult.Success ->
                            _state.update { it.copy(isLoading = false, loggedInUserId = me.value.uid) }
                        is AppResult.Failure ->
                            _state.update { it.copy(isLoading = false, error = me.error.toMessage()) }
                    }
                }
            }
        }
    }
}