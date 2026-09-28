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
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * V60 (reverte a V59 -- "ativo" agora é PROGRAMA, não treino).
 *
 * Só cobre o que esta fatia mudou (o `isActive` em `get`). O resto de [WorkoutService]
 * (create/update/delete/list) não tinha teste antes desta fatia e continua sem -- não é
 * escopo daqui reconstruir cobertura de código que eu não toquei. `activate` saiu inteiro
 * daqui, virou `ProgramService.activate` (ver ProgramServiceTest).
 */
class WorkoutServiceTest {

    private val agora = LocalDateTime(2026, 9, 24, 12, 0)

    /** 2026-09-28 é SEGUNDA (isoDayNumber 1) -- mesmo dia fixo do ProgramActiveWorkoutTest. */
    private val segunda: Clock = object : Clock {
        override fun now() = Instant.parse("2026-09-28T10:00:00Z")
    }

    private fun user(activeProgramId: Uuid? = null) = User(
        id = Uuid.random(),
        firebaseUid = "fb",
        email = null,
        isPremium = false,
        displayName = "Atleta-teste",
        code = "TESTE234",
        activeProgramId = activeProgramId,
    )

    private inner class FakeUserRepo(private var u: User) : UserRepository {
        override suspend fun findByFirebaseUid(firebaseUid: String) = AppResult.Success<User?>(u)
        override suspend fun findById(userId: Uuid) = AppResult.Success<User?>(u)
        override suspend fun findByCode(code: String) = error("não usado")
        override suspend fun updateCode(userId: Uuid, code: String) = error("não usado")
        override suspend fun create(id: Uuid, firebaseUid: String, email: String?, displayName: String, code: String) =
            error("não usado")
        override suspend fun setPremium(userId: Uuid, premium: Boolean) = error("não usado")
        override suspend fun updateDisplayName(userId: Uuid, displayName: String) = error("não usado")
        override suspend fun updateIdioma(userId: Uuid, idioma: Idioma) = error("não usado")
        override suspend fun setActiveProgram(userId: Uuid, programId: Uuid?) = error("não usado")
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

    private fun servico(userRepo: FakeUserRepo, workoutRepo: FakeWorkoutRepository, clock: Clock = segunda) = WorkoutService(
        userService = UserService(userRepo),
        repository = workoutRepo,
        exerciseRepository = FakeExerciseRepository(),
        generator = FakeWorkoutGenerator(),
        clock = clock,
    )

    @Test
    fun `get marca isActive quando o treino e do programa ativo e cai no dia de hoje`() = runBlocking {
        val programaAtivoId = Uuid.random()
        val dono = user(activeProgramId = programaAtivoId)
        val treinoId = Uuid.random()
        val treino = Workout(
            id = treinoId, userId = dono.id, name = "Push", programId = programaAtivoId, dayOfWeek = 1,
            exercises = emptyList(), createdAt = agora, updatedAt = agora,
        )
        val service = servico(FakeUserRepo(dono), FakeWorkoutRepository(mutableMapOf(treinoId to treino)))

        val resultado = service.get("fb", null, treinoId)

        assertIs<AppResult.Success<dev.rafael.contract.workout.WorkoutDto?>>(resultado)
        assertEquals(true, resultado.value?.isActive)
    }

    @Test
    fun `get nao marca isActive quando o treino e de OUTRO dia da semana`() = runBlocking {
        val programaAtivoId = Uuid.random()
        val dono = user(activeProgramId = programaAtivoId)
        val treinoId = Uuid.random()
        val treino = Workout(
            id = treinoId, userId = dono.id, name = "Push", programId = programaAtivoId, dayOfWeek = 2,
            exercises = emptyList(), createdAt = agora, updatedAt = agora,
        )
        val service = servico(FakeUserRepo(dono), FakeWorkoutRepository(mutableMapOf(treinoId to treino)))

        val resultado = service.get("fb", null, treinoId)

        assertIs<AppResult.Success<dev.rafael.contract.workout.WorkoutDto?>>(resultado)
        assertEquals(false, resultado.value?.isActive)
    }

    @Test
    fun `get nao marca isActive quando o treino e de OUTRO programa`() = runBlocking {
        val dono = user(activeProgramId = Uuid.random())
        val treinoId = Uuid.random()
        val treino = Workout(
            id = treinoId, userId = dono.id, name = "Push", programId = Uuid.random(), dayOfWeek = 1,
            exercises = emptyList(), createdAt = agora, updatedAt = agora,
        )
        val service = servico(FakeUserRepo(dono), FakeWorkoutRepository(mutableMapOf(treinoId to treino)))

        val resultado = service.get("fb", null, treinoId)

        assertIs<AppResult.Success<dev.rafael.contract.workout.WorkoutDto?>>(resultado)
        assertEquals(false, resultado.value?.isActive)
    }
}
