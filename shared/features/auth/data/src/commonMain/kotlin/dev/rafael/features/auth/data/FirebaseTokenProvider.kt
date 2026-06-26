package dev.rafael.features.auth.data

import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.auth
import dev.rafael.core.network.TokenProvider

class FirebaseTokenProvider : TokenProvider {
    override suspend fun currentToken(): String? =
        Firebase.auth.currentUser?.getIdToken(false)
}