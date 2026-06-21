package dev.rafael.feature.auth.data

import dev.rafael.core.network.TokenProvider

class FirebaseTokenProvider(
    private val repository: FirebaseAuthRepository,
) : TokenProvider {
    override suspend fun currentToken(): String? = repository.currentIdToken()
}