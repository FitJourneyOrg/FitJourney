package dev.rafael.features.auth.presentation.viewmodel

import dev.rafael.core.result.AppError
import dev.rafael.core.result.AppResult
import dev.rafael.features.auth.domain.model.AuthUser
import dev.rafael.features.auth.domain.repository.AuthRepository
import dev.rafael.features.auth.presentation.state.LoginEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest fun setup() { Dispatchers.setMain(dispatcher) }
    @AfterTest fun tearDown() { Dispatchers.resetMain() }

    /**
     * `fetchMe` nunca deveria ser chamado por este ViewModel (ver login-sem-backend, 2026-11-05):
     * a entrada depende só do Firebase, não do backend. `chamouFetchMe` prova isso, não só o
     * estado final — um fix que voltasse a chamar `fetchMe()` por engano, mas com sucesso,
     * passaria pelas asserções de estado e só esta flag denunciaria.
     */
    private class FakeAuth(
        private val signIn: AppResult<AuthUser> = AppResult.Success(AuthUser("uid-1", "a@b.com")),
    ) : AuthRepository {
        var chamouFetchMe = false

        override suspend fun signIn(email: String, password: String): AppResult<AuthUser> = signIn
        override suspend fun signUp(email: String, password: String): AppResult<AuthUser> = signIn
        override suspend fun signOut(): AppResult<Unit> = AppResult.Success(Unit)
        override suspend fun currentIdToken(): String? = null
        override suspend fun isLoggedIn(): Boolean = false
        override suspend fun usuarioLocal(): AuthUser? = null
        override suspend fun fetchMe(): AppResult<AuthUser> {
            chamouFetchMe = true
            return AppResult.Success(AuthUser("uid-1", "a@b.com"))
        }
    }

    @Test
    fun `sucesso do firebase entra mesmo com o backend fora, sem esperar fetchMe`() = runTest(dispatcher) {
        val auth = FakeAuth()
        val vm = LoginViewModel(auth)

        vm.onEvent(LoginEvent.EmailChanged("a@b.com"))
        vm.onEvent(LoginEvent.PasswordChanged("123456"))
        vm.onEvent(LoginEvent.SubmitLogin)
        advanceUntilIdle()

        val s = vm.state.value
        assertEquals("uid-1", s.loggedInUserId)
        assertNull(s.error)
        assertEquals(false, auth.chamouFetchMe)
    }

    @Test
    fun `credencial invalida nao entra e mostra o erro`() = runTest(dispatcher) {
        val auth = FakeAuth(signIn = AppResult.Failure(AppError.Unauthorized("Credenciais inválidas")))
        val vm = LoginViewModel(auth)

        vm.onEvent(LoginEvent.EmailChanged("a@b.com"))
        vm.onEvent(LoginEvent.PasswordChanged("errada"))
        vm.onEvent(LoginEvent.SubmitLogin)
        advanceUntilIdle()

        val s = vm.state.value
        assertNull(s.loggedInUserId)
        assertIs<AppError.Unauthorized>(s.error)
    }
}
