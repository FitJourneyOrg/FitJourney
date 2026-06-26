package dev.rafael.features.auth.domain.model

/** Identidade autenticada — modelo de domínio, sem tipos do Firebase. */
data class AuthUser(
    val uid: String,
    val email: String?,
)