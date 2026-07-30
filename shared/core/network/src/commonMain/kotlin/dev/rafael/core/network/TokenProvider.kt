package dev.rafael.core.network

/** Fornece o ID Token e o uid do usuário atual. Implementado pela feature auth. */
interface TokenProvider {
    suspend fun currentToken(): String?
    /** uid do usuário logado, ou null. Usado p/ chavear o cache de onboarding por usuário. */
    suspend fun currentUid(): String?
}