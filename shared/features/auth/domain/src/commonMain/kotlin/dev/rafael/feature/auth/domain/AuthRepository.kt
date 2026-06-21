package dev.rafael.feature.auth.domain

import dev.rafael.core.result.AppResult

/** Identidade autenticada — modelo de domínio, sem tipos do Firebase. */
data class AuthUser(
    val uid: String,
    val email: String?,
)

/** Contrato de autenticação. Implementado na camada data (via GitLive/Firebase). */
interface AuthRepository {
    suspend fun signIn(email: String, password: String): AppResult<AuthUser>
    suspend fun signUp(email: String, password: String): AppResult<AuthUser>
    suspend fun signOut(): AppResult<Unit>
    /** ID Token (JWT) pro header Authorization: Bearer; null se não logado. */
    suspend fun currentIdToken(): String?
}