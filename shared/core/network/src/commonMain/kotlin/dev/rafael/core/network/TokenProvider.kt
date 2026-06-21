package dev.rafael.core.network

/** Fornece o ID Token atual para autenticar requests. Implementado pela feature auth. */
fun interface TokenProvider {
    suspend fun currentToken(): String?
}