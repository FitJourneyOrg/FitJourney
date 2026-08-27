package dev.rafael.server.features.user.services

import dev.rafael.core.result.AppError
import dev.rafael.core.result.AppResult
import dev.rafael.server.features.user.db.UserRepository
import dev.rafael.server.features.user.models.User
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class UserServiceTest {

    /** `jaExiste = false` simula o PRIMEIRO acesso — o caminho em que o nome nasce. */
    private class FakeRepo(private val jaExiste: Boolean = true) : UserRepository {
        val user = User(
            id = Uuid.random(),
            firebaseUid = "fb-uid",
            email = null,
            isPremium = false,
            displayName = "Atleta-abc123",
        )
        var setPremiumCalledWith: Boolean? = null
        var criadoCom: String? = null
        var renomeadoPara: String? = null

        override suspend fun findByFirebaseUid(firebaseUid: String) =
            AppResult.Success(if (jaExiste) user else null)

        override suspend fun findById(userId: Uuid): AppResult<User?> =
            AppResult.Success(if (jaExiste && userId == user.id) user else null)

        override suspend fun create(
            id: Uuid,
            firebaseUid: String,
            email: String?,
            displayName: String,
        ): AppResult<User> {
            criadoCom = displayName
            return AppResult.Success(user.copy(id = id, email = email, displayName = displayName))
        }

        override suspend fun setPremium(userId: Uuid, premium: Boolean): AppResult<User?> {
            setPremiumCalledWith = premium
            return AppResult.Success(user.copy(isPremium = premium))
        }

        override suspend fun updateDisplayName(userId: Uuid, displayName: String): AppResult<User?> {
            renomeadoPara = displayName
            return AppResult.Success(user.copy(displayName = displayName))
        }
    }

    @Test
    fun `activatePremium liga o premium do usuario`() = runBlocking {
        val repo = FakeRepo()
        val r = UserService(repo).activatePremium("fb-uid", null)

        assertTrue(r is AppResult.Success && r.value.isPremium, "usuário volta premium")
        assertEquals(true, repo.setPremiumCalledWith, "setPremium foi chamado com true")
    }

    @Test
    fun `usuario NOVO ja nasce com nome`() = runBlocking {
        // O ponto da fatia A.0: findOrCreate roda no GET /me do splash, ANTES do onboarding.
        // Se o nome esperasse o quiz, a coluna NOT NULL não teria valor no insert.
        val repo = FakeRepo(jaExiste = false)
        val r = UserService(repo).findOrCreate("fb-novo", "rafel0017@gmail.com")

        assertTrue(r is AppResult.Success)
        assertEquals("rafel0017", repo.criadoCom, "nome derivado da parte local do e-mail")
    }

    @Test
    fun `usuario novo SEM e-mail nasce com o fallback`() = runBlocking {
        val repo = FakeRepo(jaExiste = false)
        UserService(repo).findOrCreate("fb-novo", null)

        assertTrue(
            repo.criadoCom!!.startsWith("Atleta-"),
            "sem e-mail o nome vem do id — nunca vazio, senão o NOT NULL estoura",
        )
    }

    @Test
    fun `updateDisplayName normaliza antes de gravar`() = runBlocking {
        val repo = FakeRepo()
        val r = UserService(repo).updateDisplayName("fb-uid", null, "  Rafael   Souza ")

        assertTrue(r is AppResult.Success)
        assertEquals("Rafael Souza", repo.renomeadoPara, "grava o normalizado, não o cru")
    }

    @Test
    fun `nome invalido NAO chega ao banco`() = runBlocking {
        // A validação vem antes da consulta: recusa não deve custar ida ao banco, e o servidor
        // é quem decide validade ([REGRA]), não a UI.
        val repo = FakeRepo()
        val r = UserService(repo).updateDisplayName("fb-uid", null, "R")

        assertTrue(r is AppResult.Failure && r.error is AppError.Validation)
        assertNull(repo.renomeadoPara, "o repositório não pode ter sido tocado")
    }
}
