package dev.rafael.server.features.user.services

import dev.rafael.core.result.AppResult
import dev.rafael.server.features.user.db.UserRepository
import dev.rafael.server.features.user.models.User
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class UserServiceTest {

    private class FakeRepo : UserRepository {
        val user = User(id = Uuid.random(), firebaseUid = "fb-uid", email = null, isPremium = false)
        var setPremiumCalledWith: Boolean? = null
        override suspend fun findByFirebaseUid(firebaseUid: String) = AppResult.Success<User?>(user)
        override suspend fun create(firebaseUid: String, email: String?) = AppResult.Success(user)
        override suspend fun setPremium(userId: Uuid, premium: Boolean): AppResult<User?> {
            setPremiumCalledWith = premium
            return AppResult.Success(user.copy(isPremium = premium))
        }
    }

    @Test
    fun `activatePremium liga o premium do usuario`() = runBlocking {
        val repo = FakeRepo()
        val r = UserService(repo).activatePremium("fb-uid", null)

        assertTrue(r is AppResult.Success && r.value.isPremium, "usuário volta premium")
        assertEquals(true, repo.setPremiumCalledWith, "setPremium foi chamado com true")
    }
}
