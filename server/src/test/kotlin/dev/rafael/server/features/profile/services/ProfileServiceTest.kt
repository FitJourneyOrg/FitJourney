package dev.rafael.server.features.profile.services

import dev.rafael.contract.error.ErrorCodes
import dev.rafael.contract.error.ErrorFields
import dev.rafael.contract.profile.Goal
import dev.rafael.contract.profile.Level
import dev.rafael.contract.profile.ProfileDto
import dev.rafael.contract.i18n.Idioma
import dev.rafael.contract.profile.TrainingEnvironment
import dev.rafael.contract.profile.TrainingConsistency
import dev.rafael.contract.profile.TimeOffTraining
import dev.rafael.core.result.AppError
import dev.rafael.core.result.AppResult
import dev.rafael.server.features.profile.db.ProfileRepository
import dev.rafael.server.features.profile.models.Profile
import dev.rafael.server.features.user.db.UserRepository
import dev.rafael.server.features.user.models.User
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
    private var perfilGravado: Profile? = null
    private val stubProfileRepo = object : ProfileRepository {
        override suspend fun findByUserId(userId: Uuid): AppResult<Profile?> = error("não deveria ser chamado")
        override suspend fun upsert(profile: Profile): AppResult<Profile> {
            perfilGravado = profile
            return AppResult.Success(profile)
        }
    }

    private fun service() = ProfileService(UserService(stubUserRepo), stubProfileRepo)

    /** Só pros dois testes abaixo, que PRECISAM passar do guard e tocar o repositório. */
    private fun servicoComUsuarioExistente(): ProfileService {
        val usuario = User(
            id = Uuid.random(), firebaseUid = "uid", email = null, isPremium = false,
            displayName = "Teste", code = "ABCD1234", activeProgramId = null,
        )
        val userRepo = object : UserRepository by stubUserRepo {
            override suspend fun findByFirebaseUid(firebaseUid: String) = AppResult.Success(usuario)
        }
        return ProfileService(UserService(userRepo), stubProfileRepo)
    }

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

    // ---- fatia "nível derivado e fim do gate de saúde" ----

    @Test
    fun `com as duas perguntas novas preenchidas, o nivel gravado e o derivado -- ignora o level enviado`() = runBlocking {
        val comPerguntasNovas = dto().copy(
            level = Level.ADVANCED,   // enviado pelo cliente, deve ser ignorado
            trainingConsistency = TrainingConsistency.LESS_THAN_6_MONTHS,
            timeOffTraining = TimeOffTraining.NONE,
        )

        servicoComUsuarioExistente().saveProfile("uid", null, comPerguntasNovas)

        assertEquals(Level.BEGINNER, perfilGravado?.level)
    }

    /** Caminho de falha do fallback: cliente antigo (pré-fatia-12) não manda as duas -- level enviado continua valendo. */
    @Test
    fun `sem as duas perguntas novas (cliente antigo), o level enviado continua valendo -- fallback`() = runBlocking {
        val semAsPerguntasNovas = dto().copy(level = Level.ADVANCED)

        servicoComUsuarioExistente().saveProfile("uid", null, semAsPerguntasNovas)

        assertEquals(Level.ADVANCED, perfilGravado?.level)
    }
}
