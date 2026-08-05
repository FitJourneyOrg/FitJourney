package dev.rafael.features.auth.data

import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.FirebaseAuthException
import dev.gitlive.firebase.auth.FirebaseUser
import dev.gitlive.firebase.auth.auth
import dev.rafael.core.network.clearBearerToken
import dev.rafael.core.network.httpResult
import dev.rafael.core.result.AppError
import dev.rafael.core.result.AppResult
import dev.rafael.core.result.asFailure
import dev.rafael.core.result.asSuccess
import dev.rafael.features.auth.domain.model.AuthUser
import dev.rafael.features.auth.domain.repository.AuthRepository
import io.ktor.client.HttpClient

class FirebaseAuthRepository(
    private val meDataSource: MeDataSource,
    private val httpClient: HttpClient,
) : AuthRepository {

    private val auth = Firebase.auth

    override suspend fun signIn(email: String, password: String): AppResult<AuthUser> =
        runCatching {
            val result = auth.signInWithEmailAndPassword(email, password)
            httpClient.clearBearerToken()   // sessão nova → descarta o token cacheado do usuário anterior
            result.user!!.toAuthUser()
        }.fold(
            onSuccess = { it.asSuccess() },
            onFailure = { mapAuthError(it) },
        )

    override suspend fun signUp(email: String, password: String): AppResult<AuthUser> =
        runCatching {
            val result = auth.createUserWithEmailAndPassword(email, password)
            httpClient.clearBearerToken()   // idem: novo usuário, token novo
            result.user!!.toAuthUser()
        }.fold(
            onSuccess = { it.asSuccess() },
            onFailure = { mapAuthError(it) },
        )

    override suspend fun signOut(): AppResult<Unit> =
        runCatching {
            auth.signOut()
            httpClient.clearBearerToken()   // não deixa token válido vazar p/ o próximo usuário
        }.fold(
            onSuccess = { Unit.asSuccess() },
            onFailure = { AppError.Unexpected("Falha ao sair", it).asFailure() },
        )

    /**
     * Token cacheado do usuário logado, ou null. Robusto a falha de rede: o
     * getIdToken(false) bate na rede quando o token expirou, e offline lança
     * FirebaseNetworkException — que NÃO pode derrubar o app (a Splash chama isto
     * no arranque). Falhou → null → o gate manda pro Login (degrada, não crasha).
     */
    override suspend fun currentIdToken(): String? =
        runCatching { auth.currentUser?.getIdToken(false) }.getOrNull()

    /** Sessão persistida (sobrevive offline + restart). Não bate na rede — só olha o cache local. */
    override suspend fun isLoggedIn(): Boolean = auth.currentUser != null

    override suspend fun fetchMe(): AppResult<AuthUser> =
        httpResult { meDataSource.getMe().let { AuthUser(uid = it.id, email = it.email) } }

    private fun mapAuthError(e: Throwable): AppResult<AuthUser> = when {
        e is FirebaseAuthException -> AppError.Unauthorized("Credenciais inválidas").asFailure()
        // offline no 1º login: Firebase lança erro de rede. Não dá p/ validar credenciais sem internet.
        (e.message ?: "").contains("network", ignoreCase = true) ->
            AppError.Unexpected("Sem conexão. O primeiro login precisa de internet.", e).asFailure()
        else -> AppError.Unexpected("Falha na autenticação. Verifique sua conexão.", e).asFailure()
    }
}

private fun FirebaseUser.toAuthUser() =
    AuthUser(uid = uid, email = email)