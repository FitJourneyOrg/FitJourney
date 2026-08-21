package dev.rafael.server.features.session.services

import dev.rafael.contract.session.SetLogDto
import dev.rafael.contract.session.WorkoutSessionDto
import dev.rafael.core.result.AppError
import dev.rafael.core.result.AppResult
import dev.rafael.core.result.asSuccess
import dev.rafael.server.features.session.db.SessionRepository
import dev.rafael.server.features.session.models.WorkoutSession
import dev.rafael.server.features.user.db.UserRepository
import dev.rafael.server.features.user.models.User
import dev.rafael.server.features.user.services.UserService
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class SessionServiceTest {

    private val user = User(
        id = Uuid.random(),
        firebaseUid = "fb",
        email = null,
        isPremium = false,
        displayName = "Atleta-teste",
    )

    private inner class FakeUserRepo : UserRepository {
        override suspend fun findByFirebaseUid(firebaseUid: String) = AppResult.Success<User?>(user)
        override suspend fun create(id: Uuid, firebaseUid: String, email: String?, displayName: String) =
            AppResult.Success(user)
        override suspend fun setPremium(userId: Uuid, premium: Boolean) = AppResult.Success<User?>(user)
        override suspend fun updateDisplayName(userId: Uuid, displayName: String) =
            AppResult.Success<User?>(user)
    }

    private class FakeSessionRepo : SessionRepository {
        val store = linkedMapOf<Uuid, WorkoutSession>()
        override suspend fun save(session: WorkoutSession): AppResult<Unit> {
            store.putIfAbsent(session.id, session)   // idempotente (espelha o ON CONFLICT DO NOTHING)
            return Unit.asSuccess()
        }
        override suspend fun listByUser(userId: Uuid) =
            store.values.filter { it.userId == userId }.asSuccess()
    }

    private fun service(repo: FakeSessionRepo) = SessionService(UserService(FakeUserRepo()), repo)

    private fun oneSet() = listOf(
        SetLogDto(exerciseId = Uuid.random().toString(), orderIndex = 0, setIndex = 0, targetReps = 10, repsDone = 10, weightKg = 60.0, done = true),
    )

    private fun dto(
        id: String = Uuid.random().toString(),
        sets: List<SetLogDto> = oneSet(),
        start: String = "2026-01-01T10:00:00",
        finish: String = "2026-01-01T10:40:00",
    ) = WorkoutSessionDto(id = id, workoutName = "Full Body A", startedAt = start, finishedAt = finish, sets = sets)

    @Test
    fun `record grava a sessao`() = runBlocking {
        val repo = FakeSessionRepo()
        val r = service(repo).record("fb", null, dto())
        assertIs<AppResult.Success<WorkoutSessionDto>>(r)
        assertEquals(1, repo.store.size)
    }

    @Test
    fun `record e idempotente por id`() = runBlocking {
        val repo = FakeSessionRepo()
        val d = dto()
        service(repo).record("fb", null, d)
        service(repo).record("fb", null, d)   // mesmo id (reenvio do sync)
        assertEquals(1, repo.store.size, "reenvio não duplica")
    }

    @Test
    fun `sessao sem series vira Validation`() = runBlocking {
        val r = service(FakeSessionRepo()).record("fb", null, dto(sets = emptyList()))
        assertTrue(r is AppResult.Failure && r.error is AppError.Validation)
    }

    @Test
    fun `fim antes do inicio vira Validation`() = runBlocking {
        val r = service(FakeSessionRepo()).record("fb", null, dto(start = "2026-01-01T10:40:00", finish = "2026-01-01T10:00:00"))
        assertTrue(r is AppResult.Failure && r.error is AppError.Validation)
    }
}
