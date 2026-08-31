package dev.rafael.app.data.sessao

import dev.rafael.core.result.AppResult
import dev.rafael.features.auth.domain.model.AuthUser
import dev.rafael.features.auth.domain.repository.AuthRepository
import dev.rafael.features.profile.domain.model.Profile
import dev.rafael.features.profile.domain.repository.ProfileRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A ORDEM do logout (F.1 acrescentou o terceiro passo).
 *
 * ## Por que dublês próprios, e não os da Home
 *
 * O `ContaViewModelTest` reusa `FakeAuth`/`FakePerfil` de propósito — e está certo, porque lá o
 * assunto é "o logout aconteceu". Aqui o assunto é **em que ordem**, e aqueles dublês guardam
 * booleanos: `deslogou = true` não sabe dizer se veio antes ou depois da baixa do push. Um
 * registro compartilhado é o mínimo que responde à pergunta deste arquivo.
 *
 * ## O que a ordem protege
 *
 * Dois defeitos com sintomas muito diferentes, e nenhum dos dois aparece na tela na hora:
 *
 * 1. **Push depois do `signOut`**: o token do Firebase já morreu, a requisição sai sem
 *    autenticação e o aparelho continua registrado. Quem saiu da conta segue recebendo
 *    notificação neste celular — só se percebe quando alguém empresta o aparelho.
 * 2. **Cache de onboarding depois do `signOut`**: o próximo cadastro herda
 *    `onboardingCompleted = true` e cai direto na Home, pulando o quiz. É um defeito da Fase 5
 *    que já voltou uma vez.
 */
class SairDaContaTest {

    /** O registro compartilhado — é ele que transforma "aconteceu" em "aconteceu quando". */
    private val passos = mutableListOf<String>()

    private inner class AuthQueRegistra : AuthRepository {
        override suspend fun signOut(): AppResult<Unit> {
            passos += "signOut"
            return AppResult.Success(Unit)
        }
        override suspend fun signIn(email: String, password: String) = error("não usado")
        override suspend fun signUp(email: String, password: String) = error("não usado")
        override suspend fun isLoggedIn(): Boolean = true
        override suspend fun currentIdToken(): String? = "t"
        override suspend fun fetchMe(): AppResult<AuthUser> = error("não usado")
    }

    private inner class PerfilQueRegistra : ProfileRepository {
        override suspend fun clearOnboardingCache() { passos += "limparOnboarding" }
        override suspend fun getProfile() = error("não usado")
        override suspend fun saveProfile(profile: Profile) = error("não usado")
        override suspend fun cachedOnboardingCompleted(): Boolean? = true
    }

    /**
     * [INVARIANTE] A baixa do push vem PRIMEIRO, o `signOut` vem POR ÚLTIMO.
     *
     * A lista inteira é comparada de uma vez, e não em três asserts separados: o que está sendo
     * afirmado aqui é a sequência, e um assert por passo permitiria que ela se embaralhasse sem
     * o teste notar.
     */
    @Test
    fun `os tres passos saem na ordem, com a baixa do push na frente`() = runTest {
        val sair = SairDaConta(AuthQueRegistra(), PerfilQueRegistra()) { passos += "baixaDoPush" }

        sair()

        assertEquals(
            listOf("baixaDoPush", "limparOnboarding", "signOut"),
            passos,
            "o push precisa do token AINDA VÁLIDO, e o cache precisa morrer antes da sessão",
        )
    }
}
