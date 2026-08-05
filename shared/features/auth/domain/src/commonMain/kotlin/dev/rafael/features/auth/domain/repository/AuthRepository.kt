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
    /**
     * Já existe uma sessão persistida neste device? (Firebase guarda o currentUser mesmo
     * offline.) Usado pelo gate da Splash: quem já logou entra mesmo sem rede — só vai pro
     * Login quem NUNCA logou aqui (1º login exige internet p/ validar as credenciais).
     */
    suspend fun isLoggedIn(): Boolean
    /** Valida a sessão no backend: GET /me com o Bearer. Confirma que o login é reconhecido pelo servidor. */
    suspend fun fetchMe(): AppResult<AuthUser>
}