package dev.rafael.server.features.workout.services

import dev.rafael.contract.exercise.ExerciseCategory
import dev.rafael.contract.i18n.Idioma
import dev.rafael.contract.profile.ProfileDto
import dev.rafael.contract.program.ProgramDto
import dev.rafael.core.result.AppResult
import dev.rafael.core.result.asSuccess
import dev.rafael.server.features.exercise.db.ExerciseRepository
import dev.rafael.server.features.exercise.engine.WorkoutGenerator
import dev.rafael.server.features.exercise.models.Exercise
import dev.rafael.server.features.user.db.UserRepository
import dev.rafael.server.features.user.models.User
import dev.rafael.server.features.user.services.UserService
import dev.rafael.server.features.workout.db.WorkoutRepository
import dev.rafael.server.features.workout.models.Workout
import dev.rafael.server.features.workout.models.WorkoutSummary
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.uuid.Uuid

/**
 * V59 — "treino ativo" (ponteiro simples, exclusivo, autoridade do servidor).
 *
 * Só cobre o que esta fatia mudou (`activate` e o `isActive` em `list`). O resto de
 * [WorkoutService] (create/get/update/delete) não tinha teste antes desta fatia e continua
 * sem — não é escopo daqui reconstruir cobertura de código que eu não toquei.
 */
class WorkoutServiceTest {

    private val agora = LocalDateTime(2026, 9, 24, 12, 0)

    private fun user(activeWorkoutId: Uuid? = null) = User(
        id = Uuid.random(),
        firebaseUid = "fb",
        email = null,
        isPremium = false,
        displayName = "Atleta-teste",
        code = "TESTE234",
        activeWorkoutId = activeWorkoutId,
    )

    private inner class FakeUserRepo(private var u: User) : UserRepository {
        var setActiveWorkoutCalledWith: Uuid? = null
        var setActiveWorkoutCallCount = 0

        override suspend fun findByFirebaseUid(firebaseUid: String) = AppResult.Success<User?>(u)
        override suspend fun findById(userId: Uuid) = AppResult.Success<User?>(u)
        override suspend fun findByCode(code: String) = error("não usado")
        override suspend fun updateCode(userId: Uuid, code: String) = error("não usado")
        override suspend fun create(id: Uuid, firebaseUid: String, email: String?, displayName: String, code: String) =
            error("não usado")
        override suspend fun setPremium(userId: Uuid, premium: Boolean) = error("não usado")
        override suspend fun updateDisplayName(userId: Uuid, displayName: String) = error("não usado")
        override suspend fun updateIdioma(userId: Uuid, idioma: Idioma) = error("não usado")

        override suspend fun setActiveWorkout(userId: Uuid, workoutId: Uuid?): AppResult<User?> {
            setActiveWorkoutCallCount++
            setActiveWorkoutCalledWith = workoutId
            u = u.copy(activeWorkoutId = workoutId)
            return AppResult.Success(u)
        }
    }

    private class FakeWorkoutRepository(
        private val workoutsDoUsuario: MutableMap<Uuid, Workout> = mutableMapOf(),
        private val resumos: List<WorkoutSummary> = emptyList(),
    ) : WorkoutRepository {
        override suspend fun create(userId: Uuid, workout: Workout, programId: Uuid, dayOfWeek: Int) =
            error("não usado")

        override suspend fun findAllByUser(userId: Uuid): AppResult<List<WorkoutSummary>> =
            resumos.asSuccess()

        // Espelha o comportamento real: só acha o treino se for DESTE usuário (posse).
        override suspend fun findById(userId: Uuid, workoutId: Uuid): AppResult<Workout?> =
            AppResult.Success(workoutsDoUsuario[workoutId]?.takeIf { it.userId == userId })

        override suspend fun update(userId: Uuid, workoutId: Uuid, workout: Workout) = error("não usado")
        override suspend fun delete(userId: Uuid, workoutId: Uuid) = error("não usado")
    }

    private class FakeExerciseRepository : ExerciseRepository {
        override suspend fun findAll(idioma: Idioma) = error("não usado")
        override suspend fun findByCategory(category: ExerciseCategory, idioma: Idioma) = error("não usado")
        override suspend fun findById(id: Uuid, idioma: Idioma) = error("não usado")
        override suspend fun existsByIds(ids: List<Uuid>) = error("não usado")
        override suspend fun nomesTraduzidos(ids: List<Uuid>, idioma: Idioma) = error("não usado")
    }

    private class FakeWorkoutGenerator : WorkoutGenerator {
        override suspend fun generate(profile: ProfileDto, prompt: String?): ProgramDto = error("não usado")
    }

    private fun servico(userRepo: FakeUserRepo, workoutRepo: FakeWorkoutRepository) = WorkoutService(
        userService = UserService(userRepo),
        repository = workoutRepo,
        exerciseRepository = FakeExerciseRepository(),
        generator = FakeWorkoutGenerator(),
        userRepository = userRepo,
    )

    @Test
    fun `activate marca o treino como ativo quando pertence ao usuario`() = runBlocking {
        val dono = user()
        val treinoId = Uuid.random()
        val treino = Workout(
            id = treinoId, userId = dono.id, name = "Push", programId = null, dayOfWeek = null,
            exercises = emptyList(), createdAt = agora, updatedAt = agora,
        )
        val userRepo = FakeUserRepo(dono)
        val service = servico(userRepo, FakeWorkoutRepository(mutableMapOf(treinoId to treino)))

        val resultado = service.activate("fb", null, treinoId)

        assertIs<AppResult.Success<Unit>>(resultado)
        assertEquals(1, userRepo.setActiveWorkoutCallCount, "setActiveWorkout deveria ter sido chamado uma vez")
        assertEquals(treinoId, userRepo.setActiveWorkoutCalledWith)
    }

    @Test
    fun `activate falha e nao mexe no ponteiro quando o treino nao e do usuario (ou nao existe)`() = runBlocking {
        val dono = user()
        val treinoDeOutroUsuario = Uuid.random()
        val treino = Workout(
            id = treinoDeOutroUsuario, userId = Uuid.random(), name = "Push", programId = null,
            dayOfWeek = null, exercises = emptyList(), createdAt = agora, updatedAt = agora,
        )
        val userRepo = FakeUserRepo(dono)
        val service = servico(userRepo, FakeWorkoutRepository(mutableMapOf(treinoDeOutroUsuario to treino)))

        val resultado = service.activate("fb", null, treinoDeOutroUsuario)

        assertIs<AppResult.Failure>(resultado)
        assertEquals(0, userRepo.setActiveWorkoutCallCount, "não pode gravar ponteiro pra treino que não é do usuário")
        assertNull(userRepo.setActiveWorkoutCalledWith)
    }

    @Test
    fun `list marca isActive so no treino que bate com o ponteiro do usuario`() = runBlocking {
        val ativoId = Uuid.random()
        val dono = user(activeWorkoutId = ativoId)
        val outroId = Uuid.random()
        val resumos = listOf(
            WorkoutSummary(id = ativoId, name = "Push", exerciseCount = 8, updatedAt = agora),
            WorkoutSummary(id = outroId, name = "Lower", exerciseCount = 6, updatedAt = agora),
        )
        val userRepo = FakeUserRepo(dono)
        val service = servico(userRepo, FakeWorkoutRepository(resumos = resumos))

        val resultado = service.list("fb", null)

        assertIs<AppResult.Success<List<dev.rafael.contract.workout.WorkoutSummaryDto>>>(resultado)
        val porId = resultado.value.associateBy { it.id }
        assertEquals(true, porId[ativoId.toString()]?.isActive)
        assertEquals(false, porId[outroId.toString()]?.isActive)
    }

    @Test
    fun `get marca isActive quando o treino lido e o ativo do usuario`() = runBlocking {
        val ativoId = Uuid.random()
        val dono = user(activeWorkoutId = ativoId)
        val treino = Workout(
            id = ativoId, userId = dono.id, name = "Push", programId = null, dayOfWeek = null,
            exercises = emptyList(), createdAt = agora, updatedAt = agora,
        )
        val userRepo = FakeUserRepo(dono)
        val service = servico(userRepo, FakeWorkoutRepository(mutableMapOf(ativoId to treino)))

        val resultado = service.get("fb", null, ativoId)

        assertIs<AppResult.Success<dev.rafael.contract.workout.WorkoutDto?>>(resultado)
        assertEquals(true, resultado.value?.isActive)
    }
}
