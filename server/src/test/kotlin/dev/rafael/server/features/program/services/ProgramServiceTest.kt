package dev.rafael.server.features.program.services

import dev.rafael.contract.error.ErrorCodes
import dev.rafael.contract.profile.Goal
import dev.rafael.contract.profile.Level
import dev.rafael.contract.profile.ProfileDto
import dev.rafael.contract.profile.TrainingEnvironment
import dev.rafael.contract.program.ProgramDto
import dev.rafael.contract.workout.WorkoutOrigin
import dev.rafael.core.result.AppError
import dev.rafael.core.result.AppResult
import dev.rafael.core.result.asSuccess
import dev.rafael.server.features.exercise.engine.WorkoutGenerator
import dev.rafael.server.features.program.db.ProgramRepository
import dev.rafael.server.features.program.models.Program
import dev.rafael.server.features.program.models.ProgramCounts
import dev.rafael.server.features.workout.models.Workout
import kotlinx.datetime.LocalDateTime
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/**
 * Testa o ProgramService — regras de gerar/criar/renomear/origem — com FAKES à mão
 * (sem mockk): um repositório em memória e um gerador canned. Prova o que é lógica
 * do service; o gate de teto (grátis 1 IA + 2 manual) vive na ROTA, não aqui.
 */
class ProgramServiceTest {

    private val user = Uuid.random()

    // ---------- fakes ----------
    private class FakeRepo : ProgramRepository {
        val store = linkedMapOf<Uuid, Program>()
        var countsValue = ProgramCounts(ai = 0, manual = 0)
        var deleteValue = true

        override suspend fun counts(userId: Uuid) = countsValue.asSuccess()

        override suspend fun createForUser(userId: Uuid, program: Program): AppResult<Program> {
            val saved = program.copy(id = Uuid.random())
            store[saved.id] = saved
            return saved.asSuccess()
        }

        override suspend fun findAllByUser(userId: Uuid) =
            store.values.filter { it.userId == userId }.asSuccess()

        override suspend fun findByIdForUser(userId: Uuid, programId: Uuid) =
            (store[programId]?.takeIf { it.userId == userId }).asSuccess()

        override suspend fun rename(userId: Uuid, programId: Uuid, name: String): AppResult<Program?> {
            val p = store[programId]?.takeIf { it.userId == userId } ?: return AppResult.Success(null)
            val updated = p.copy(name = name)
            store[programId] = updated
            return updated.asSuccess()
        }

        override suspend fun delete(userId: Uuid, programId: Uuid) = deleteValue.asSuccess()

        override suspend fun reorderSchedule(
            userId: Uuid,
            programId: Uuid,
            orderedWorkoutIds: List<Uuid>,
        ): AppResult<Program?> {
            val p = store[programId]?.takeIf { it.userId == userId } ?: return AppResult.Success(null)
            val byId = p.workouts.associateBy { it.id }
            val reordered = orderedWorkoutIds.mapNotNull { byId[it] }
            val updated = p.copy(workouts = reordered)
            store[programId] = updated
            return updated.asSuccess()
        }
    }

    private class FakeGenerator(private val throwInvalid: Boolean = false) : WorkoutGenerator {
        override suspend fun generate(profile: ProfileDto, prompt: String?): ProgramDto {
            if (throwInvalid) throw IllegalArgumentException("environment obrigatório")
            return ProgramDto(
                id = "", name = "", origin = WorkoutOrigin.AI,
                workouts = emptyList(), daysPerWeek = 3, split = "Full Body",
                rationale = "r", locked = false, schedule = emptyList(),
            )
        }
    }

    private fun profile() = ProfileDto(
        goal = Goal.GAIN_MUSCLE,
        level = Level.INTERMEDIATE,
        daysPerWeek = 3,
        focusAreas = emptyList(),
        environment = TrainingEnvironment.ACADEMIA,
        onboardingCompleted = true,
    )

    private fun service(repo: FakeRepo = FakeRepo(), gen: FakeGenerator = FakeGenerator()) =
        ProgramService(gen, repo)

    // ---------- generate ----------

    @Test
    fun `generate persiste e devolve o programa como AI`() = runBlocking {
        val repo = FakeRepo()
        val r = service(repo).generate(user, profile())

        assertIs<AppResult.Success<ProgramDto>>(r)
        assertEquals(1, repo.store.size, "deveria ter persistido 1 programa")
        assertEquals(WorkoutOrigin.AI, r.value.origin)
        assertTrue(r.value.name.startsWith("Programa"), "nome automático")
    }

    @Test
    fun `generate com perfil incompleto vira Validation`() = runBlocking {
        val r = service(gen = FakeGenerator(throwInvalid = true)).generate(user, profile())

        assertTrue(r is AppResult.Failure && r.error is AppError.Validation)
    }

    // ---------- createManual ----------

    @Test
    fun `createManual exige nome`() = runBlocking {
        val r = service().createManual(user, "   ")
        assertTrue(r is AppResult.Failure && r.error is AppError.Validation)
    }

    @Test
    fun `createManual cria shell MANUAL com o nome`() = runBlocking {
        val r = service().createManual(user, "Meu programa")

        assertIs<AppResult.Success<ProgramDto>>(r)
        assertEquals("Meu programa", r.value.name)
        assertEquals(WorkoutOrigin.MANUAL, r.value.origin)
    }

    // ---------- rename ----------

    @Test
    fun `rename exige nome`() = runBlocking {
        val r = service().rename(user, Uuid.random(), " ")
        assertTrue(r is AppResult.Failure && r.error is AppError.Validation)
    }

    @Test
    fun `rename de programa inexistente vira NotFound`() = runBlocking {
        val r = service().rename(user, Uuid.random(), "Novo nome")
        assertTrue(r is AppResult.Failure && r.error is AppError.NotFound)
    }

    @Test
    fun `rename troca o nome do programa`() = runBlocking {
        val repo = FakeRepo()
        val svc = service(repo)
        val created = (svc.createManual(user, "Antigo") as AppResult.Success).value

        val r = svc.rename(user, Uuid.parse(created.id!!), "Novo")

        assertIs<AppResult.Success<ProgramDto>>(r)
        assertEquals("Novo", r.value.name)
    }

    // ---------- origem / posse ----------

    @Test
    fun `originOf devolve a origem do programa do usuario`() = runBlocking {
        val repo = FakeRepo()
        val svc = service(repo)
        val created = (svc.createManual(user, "X") as AppResult.Success).value

        val r = svc.originOf(user, Uuid.parse(created.id!!))

        assertEquals(WorkoutOrigin.MANUAL, (r as AppResult.Success).value)
    }

    @Test
    fun `originOf devolve null quando o programa nao e do usuario`() = runBlocking {
        val repo = FakeRepo()
        val svc = service(repo)
        val created = (svc.createManual(user, "X") as AppResult.Success).value

        val r = svc.originOf(Uuid.random(), Uuid.parse(created.id!!))   // outro usuário

        assertNull((r as AppResult.Success).value)
    }

    @Test
    fun `workoutCountForOwner e null quando nao e dono`() = runBlocking {
        val repo = FakeRepo()
        val svc = service(repo)
        val created = (svc.createManual(user, "X") as AppResult.Success).value

        val r = svc.workoutCountForOwner(Uuid.random(), Uuid.parse(created.id!!))

        assertNull((r as AppResult.Success).value)
    }

    // ---------- gate de edição premium (ARCH #25) ----------

    @Test
    fun `requireEditable bloqueia programa IA para usuario free`() = runBlocking {
        val repo = FakeRepo()
        val svc = service(repo)
        val ai = (svc.generate(user, profile()) as AppResult.Success).value

        val r = svc.requireEditable(user, Uuid.parse(ai.id!!), isPremium = false)

        assertTrue(r is AppResult.Failure && r.error is AppError.Forbidden)
        assertEquals(ErrorCodes.ENTITLEMENT_REQUIRED, ((r as AppResult.Failure).error as AppError.Forbidden).code)
    }

    @Test
    fun `requireEditable libera programa IA para premium`() = runBlocking {
        val repo = FakeRepo()
        val svc = service(repo)
        val ai = (svc.generate(user, profile()) as AppResult.Success).value

        val r = svc.requireEditable(user, Uuid.parse(ai.id!!), isPremium = true)

        assertIs<AppResult.Success<Unit>>(r)
        Unit   // @Test precisa retornar void; assertIs devolve valor
    }

    @Test
    fun `requireEditable libera programa manual mesmo para free`() = runBlocking {
        val repo = FakeRepo()
        val svc = service(repo)
        val manual = (svc.createManual(user, "Meu") as AppResult.Success).value

        val r = svc.requireEditable(user, Uuid.parse(manual.id!!), isPremium = false)

        assertIs<AppResult.Success<Unit>>(r)
        Unit   // @Test precisa retornar void; assertIs devolve valor
    }

    @Test
    fun `requireEditable vira NotFound quando o programa nao e do usuario`() = runBlocking {
        val repo = FakeRepo()
        val svc = service(repo)
        val ai = (svc.generate(user, profile()) as AppResult.Success).value

        val r = svc.requireEditable(Uuid.random(), Uuid.parse(ai.id!!), isPremium = false)

        assertTrue(r is AppResult.Failure && r.error is AppError.NotFound)
    }

    // ---------- reorderSchedule (G.2 agendamento) ----------

    private fun seedProgram(repo: FakeRepo, vararg workoutIds: Uuid): Uuid {
        val ts = LocalDateTime(2026, 1, 1, 0, 0)
        val pid = Uuid.random()
        val workouts = workoutIds.mapIndexed { i, wid ->
            Workout(id = wid, userId = user, name = "Dia ${i + 1}", programId = pid, exercises = emptyList(), createdAt = ts, updatedAt = ts)
        }
        repo.store[pid] = Program(
            id = pid, userId = user, name = "P", origin = WorkoutOrigin.AI, daysPerWeek = workoutIds.size,
            split = "x", rationale = "", locked = false, workouts = workouts, createdAt = ts, updatedAt = ts,
        )
        return pid
    }

    @Test
    fun `reorder aplica a nova ordem dos treinos`() = runBlocking {
        val repo = FakeRepo()
        val (w1, w2, w3) = Triple(Uuid.random(), Uuid.random(), Uuid.random())
        val pid = seedProgram(repo, w1, w2, w3)

        val r = service(repo).reorderSchedule(user, pid, listOf(w3.toString(), w1.toString(), w2.toString()))

        assertIs<AppResult.Success<ProgramDto>>(r)
        assertEquals(listOf(w3.toString(), w1.toString(), w2.toString()), r.value.workouts.map { it.id })
        // schedule deriva da ordem: day 1,2,3 na nova sequência
        assertEquals(listOf(1, 2, 3), r.value.schedule.map { it.dayOfWeek })
    }

    @Test
    fun `reorder com ordem vazia vira Validation`() = runBlocking {
        val repo = FakeRepo()
        val pid = seedProgram(repo, Uuid.random(), Uuid.random())
        val r = service(repo).reorderSchedule(user, pid, emptyList())
        assertTrue(r is AppResult.Failure && r.error is AppError.Validation)
    }

    @Test
    fun `reorder com id repetido vira Validation`() = runBlocking {
        val repo = FakeRepo()
        val w1 = Uuid.random(); val w2 = Uuid.random()
        val pid = seedProgram(repo, w1, w2)
        val r = service(repo).reorderSchedule(user, pid, listOf(w1.toString(), w1.toString()))
        assertTrue(r is AppResult.Failure && r.error is AppError.Validation)
    }

    @Test
    fun `reorder que nao bate com os treinos vira Validation`() = runBlocking {
        val repo = FakeRepo()
        val w1 = Uuid.random(); val w2 = Uuid.random()
        val pid = seedProgram(repo, w1, w2)
        // manda um id que não é do programa
        val r = service(repo).reorderSchedule(user, pid, listOf(w1.toString(), Uuid.random().toString()))
        assertTrue(r is AppResult.Failure && r.error is AppError.Validation)
    }

    @Test
    fun `reorder de programa inexistente vira NotFound`() = runBlocking {
        val r = service().reorderSchedule(user, Uuid.random(), listOf(Uuid.random().toString()))
        assertTrue(r is AppResult.Failure && r.error is AppError.NotFound)
    }

    // ---------- passthrough ----------

    @Test
    fun `delete repassa o resultado do repositorio`() = runBlocking {
        val repo = FakeRepo().apply { deleteValue = false }
        val r = service(repo).delete(user, Uuid.random())
        assertEquals(false, (r as AppResult.Success).value)
    }

    @Test
    fun `counts repassa a contagem do repositorio`() = runBlocking {
        val repo = FakeRepo().apply { countsValue = ProgramCounts(ai = 1, manual = 2) }
        val r = service(repo).counts(user)

        val counts = (r as AppResult.Success).value
        assertEquals(1, counts.ai)
        assertEquals(2, counts.manual)
        assertEquals(3, counts.total)
    }
}
