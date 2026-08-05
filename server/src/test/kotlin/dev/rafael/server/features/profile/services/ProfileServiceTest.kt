package dev.rafael.server.features.profile.services

import dev.rafael.contract.profile.Goal
import dev.rafael.contract.profile.Level
import dev.rafael.contract.profile.ProfileDto
import dev.rafael.contract.profile.TrainingEnvironment
import dev.rafael.core.result.AppError
import dev.rafael.core.result.AppResult
import dev.rafael.server.features.profile.db.ProfileRepository
import dev.rafael.server.features.profile.models.Profile
import dev.rafael.server.features.user.db.UserRepository
import dev.rafael.server.features.user.services.UserService
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/**
 * Valida os guards do saveProfile (Estágio 2 — dias off). Esses guards retornam ANTES de
 * tocar userService/repository, então os stubs abaixo nunca são chamados nos casos inválidos.
 */
class ProfileServiceTest {

    private val stubUserRepo = object : UserRepository {
        override suspend fun findByFirebaseUid(firebaseUid: String) = error("não deveria ser chamado")
        override suspend fun create(firebaseUid: String, email: String?) = error("não deveria ser chamado")
        override suspend fun setPremium(userId: kotlin.uuid.Uuid, premium: Boolean) = error("não deveria ser chamado")
    }
    private val stubProfileRepo = object : ProfileRepository {
        override suspend fun findByUserId(userId: Uuid): AppResult<Profile?> = error("não deveria ser chamado")
        override suspend fun upsert(profile: Profile): AppResult<Profile> = error("não deveria ser chamado")
    }

    private fun service() = ProfileService(UserService(stubUserRepo), stubProfileRepo)

    private fun dto(days: Int = 3, off: List<Int> = emptyList()) = ProfileDto(
        goal = Goal.GAIN_MUSCLE,
        level = Level.INTERMEDIATE,
        daysPerWeek = days,
        unavailableDays = off,
        focusAreas = emptyList(),
        environment = TrainingEnvironment.ACADEMIA,
        onboardingCompleted = false,
    )

    @Test
    fun `dias off demais para o daysPerWeek vira Validation`() = runBlocking {
        // 4 dias off → só 3 livres, mas quer treinar 4x
        val r = service().saveProfile("uid", null, dto(days = 4, off = listOf(1, 2, 3, 4)))
        assertTrue(r is AppResult.Failure && r.error is AppError.Validation)
    }

    @Test
    fun `dia off fora de 1 a 7 vira Validation`() = runBlocking {
        val r = service().saveProfile("uid", null, dto(off = listOf(9)))
        assertTrue(r is AppResult.Failure && r.error is AppError.Validation)
    }

    @Test
    fun `dia off repetido vira Validation`() = runBlocking {
        val r = service().saveProfile("uid", null, dto(off = listOf(2, 2)))
        assertTrue(r is AppResult.Failure && r.error is AppError.Validation)
    }
}
