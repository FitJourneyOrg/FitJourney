package dev.rafael.server.features.profile.services

import dev.rafael.contract.error.ErrorCodes
import dev.rafael.contract.error.ErrorFields
import dev.rafael.contract.profile.Goal
import dev.rafael.contract.profile.Level
import dev.rafael.contract.profile.ProfileDto
import dev.rafael.contract.i18n.Idioma
import dev.rafael.contract.profile.TrainingEnvironment
import dev.rafael.core.result.AppError
import dev.rafael.core.result.AppResult
import dev.rafael.server.features.profile.db.ProfileRepository
import dev.rafael.server.features.profile.models.Profile
import dev.rafael.server.features.user.db.UserRepository
import dev.rafael.server.features.user.services.UserService
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/**
 * Valida os guards do saveProfile (Estágio 2 — dias off). Esses guards retornam ANTES de
 * tocar userService/repository, então os stubs abaixo nunca são chamados nos casos inválidos.
 */
class ProfileServiceTest {

    private val stubUserRepo = object : UserRepository {
        override suspend fun findByFirebaseUid(firebaseUid: String) = error("não deveria ser chamado")
        override suspend fun findById(userId: kotlin.uuid.Uuid) = error("não deveria ser chamado")
        override suspend fun findByCode(code: String) = error("não deveria ser chamado")
        override suspend fun updateCode(userId: kotlin.uuid.Uuid, code: String) = error("não deveria ser chamado")
        override suspend fun create(
            id: kotlin.uuid.Uuid,
            firebaseUid: String,
            email: String?,
            displayName: String,
            code: String,
        ) = error("não deveria ser chamado")
        override suspend fun setPremium(userId: kotlin.uuid.Uuid, premium: Boolean) = error("não deveria ser chamado")
        override suspend fun setActiveProgram(userId: kotlin.uuid.Uuid, programId: kotlin.uuid.Uuid?) = error("não deveria ser chamado")
        override suspend fun updateDisplayName(userId: kotlin.uuid.Uuid, displayName: String) =
            error("não deveria ser chamado")
        override suspend fun updateIdioma(userId: kotlin.uuid.Uuid, idioma: Idioma) =
            error("não deveria ser chamado")
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

    // ---- débito "erroDoCampo devolve frase do servidor" (debitos.md, P2, 2026-09-23) ----
    // fieldErrors carrega o CÓDIGO, não a frase — o cliente é quem traduz agora.

    @Test
    fun `daysPerWeek fora de 2 a 6 recusa com o codigo em fieldErrors`() = runBlocking {
        val r = service().saveProfile("uid", null, dto(days = 7))

        val erro = (r as AppResult.Failure).error as AppError.Validation
        assertEquals(ErrorCodes.DIAS_POR_SEMANA_INVALIDO, erro.fieldErrors[ErrorFields.DAYS_PER_WEEK])
    }

    @Test
    fun `mais de 2 grupos de foco recusa com o codigo em fieldErrors`() = runBlocking {
        val comFocoDemais = dto().copy(
            focusAreas = listOf(
                dev.rafael.contract.profile.MuscleGroup.CHEST,
                dev.rafael.contract.profile.MuscleGroup.BACK,
                dev.rafael.contract.profile.MuscleGroup.LEGS,
            ),
        )
        val r = service().saveProfile("uid", null, comFocoDemais)

        val erro = (r as AppResult.Failure).error as AppError.Validation
        assertEquals(ErrorCodes.FOCO_ALEM_DO_LIMITE, erro.fieldErrors[ErrorFields.FOCUS_AREAS])
    }

    /** Caminho de falha do trio: idade fora de 5..120 — e idade `null` (não respondeu) segue válida. */
    @Test
    fun `idade fora de 5 a 120 recusa com o codigo em fieldErrors, idade null continua valida`() = runBlocking {
        val r = service().saveProfile("uid", null, dto().copy(age = 121))

        val erro = (r as AppResult.Failure).error as AppError.Validation
        assertEquals(ErrorCodes.IDADE_INVALIDA, erro.fieldErrors[ErrorFields.AGE])
    }

    @Test
    fun `dia off fora de 1 a 7 recusa com o codigo em fieldErrors`() = runBlocking {
        val r = service().saveProfile("uid", null, dto(off = listOf(8)))

        val erro = (r as AppResult.Failure).error as AppError.Validation
        assertEquals(ErrorCodes.DIAS_INDISPONIVEIS_INVALIDOS, erro.fieldErrors[ErrorFields.UNAVAILABLE_DAYS])
    }
}
