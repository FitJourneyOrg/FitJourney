package dev.rafael.features.auth.presentation.state

sealed interface LoginEvent {
    data class EmailChanged(val value: String) : LoginEvent
    data class PasswordChanged(val value: String) : LoginEvent
    data object SubmitLogin : LoginEvent
    data object SubmitSignUp : LoginEvent
}