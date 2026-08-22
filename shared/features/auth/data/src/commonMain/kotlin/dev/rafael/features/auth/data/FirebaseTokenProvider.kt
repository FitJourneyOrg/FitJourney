package dev.rafael.features.auth.data

import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.auth
import dev.rafael.core.network.TokenProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class FirebaseTokenProvider : TokenProvider {
    override suspend fun currentToken(): String? =
        Firebase.auth.currentUser?.getIdToken(false)

    override suspend fun currentUid(): String? =
        Firebase.auth.currentUser?.uid

    /**
     * O próprio Firebase já expõe a sessão como fluxo. `distinctUntilChanged` porque o
     * `authStateChanged` também emite em renovação de token, e re-chavear o cache a cada
     * renovação faria toda tela observando cache reiniciar a coleta sem motivo.
     */
    override fun uidFlow(): Flow<String?> =
        Firebase.auth.authStateChanged.map { it?.uid }.distinctUntilChanged()
}