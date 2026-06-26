package dev.rafael.features.auth.domain.repository

import dev.rafael.core.result.AppResult
import dev.rafael.features.auth.domain.model.AuthUser


/** Contrato de autenticação. Implementado na camada data (via GitLive/Firebase). */
interface AuthRepository {
    suspend fun signIn(email: String, password: String): AppResult<AuthUser>
    suspend fun signUp(email: String, password: String): AppResult<AuthUser>
    suspend fun signOut(): AppResult<Unit>

    /** ID Token (JWT) pro header Authorization: Bearer; null se não logado. */
    suspend fun currentIdToken(): String?
    /** Valida a sessão no backend: GET /me com o Bearer. Confirma que o login é reconhecido pelo servidor. */
    suspend fun fetchMe(): AppResult<AuthUser>
}