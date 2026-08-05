package dev.rafael.server.features.session.db

import dev.rafael.core.result.AppResult
import dev.rafael.server.features.session.models.WorkoutSession
import kotlin.uuid.Uuid

interface SessionRepository {
    /** Grava a sessão. IDEMPOTENTE: id já existente → não regrava (ON CONFLICT DO NOTHING). */
    suspend fun save(session: WorkoutSession): AppResult<Unit>
    /** Histórico do usuário, mais recente primeiro. */
    suspend fun listByUser(userId: Uuid): AppResult<List<WorkoutSession>>
}
