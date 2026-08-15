package dev.rafael.features.auth.presentation.state

import dev.rafael.core.result.AppError

data class LoginState(
    val email: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val error: AppError? = null,
    val loggedInUserId: String? = null,   // != null = logado e validado no backend
)