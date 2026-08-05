package dev.rafael.server.features.session.db

import dev.rafael.core.result.AppError
import dev.rafael.core.result.AppResult
import dev.rafael.core.result.asFailure
import dev.rafael.core.result.asSuccess
import dev.rafael.server.features.session.models.SetLog
import dev.rafael.server.features.session.models.WorkoutSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.time.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.uuid.Uuid

private fun now() = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())

class SessionRepositoryImpl : SessionRepository {

    override suspend fun save(session: WorkoutSession): AppResult<Unit> = dbQuery {
        // insertIgnore = ON CONFLICT DO NOTHING (por id). Se já existia (reenvio do sync),
        // insertedCount == 0 → NÃO regrava os set_logs → zero duplicata.
        val inserted = WorkoutSessionsTable.insertIgnore {
            it[id] = session.id
            it[userId] = session.userId
            it[programId] = session.programId
            it[workoutId] = session.workoutId
            it[workoutName] = session.workoutName
            it[startedAt] = session.startedAt
            it[finishedAt] = session.finishedAt
            it[createdAt] = now()   // relógio do SERVIDOR (momento do sync)
        }
        if (inserted.insertedCount > 0) {
            session.sets.forEach { s ->
                SessionSetLogsTable.insert {
                    it[id] = Uuid.random()
                    it[sessionId] = session.id
                    it[exerciseId] = s.exerciseId
                    it[orderIndex] = s.orderIndex
                    it[setIndex] = s.setIndex
                    it[targetReps] = s.targetReps
                    it[repsDone] = s.repsDone
                    it[weightKg] = s.weightKg
                    it[done] = s.done
                }
            }
        }
    }

    override suspend fun listByUser(userId: Uuid): AppResult<List<WorkoutSession>> = dbQuery {
        WorkoutSessionsTable.selectAll()
            .where { WorkoutSessionsTable.userId eq userId }
            .orderBy(WorkoutSessionsTable.finishedAt, SortOrder.DESC)
            .map { it.toSession() }
    }

    private fun ResultRow.toSession(): WorkoutSession {
        val sid = this[WorkoutSessionsTable.id]
        val sets = SessionSetLogsTable.selectAll()
            .where { SessionSetLogsTable.sessionId eq sid }
            .map {
                SetLog(
                    exerciseId = it[SessionSetLogsTable.exerciseId],
                    orderIndex = it[SessionSetLogsTable.orderIndex],
                    setIndex = it[SessionSetLogsTable.setIndex],
                    targetReps = it[SessionSetLogsTable.targetReps],
                    repsDone = it[SessionSetLogsTable.repsDone],
                    weightKg = it[SessionSetLogsTable.weightKg],
                    done = it[SessionSetLogsTable.done],
                )
            }
            .sortedWith(compareBy({ it.orderIndex }, { it.setIndex }))
        return WorkoutSession(
            id = sid,
            userId = this[WorkoutSessionsTable.userId],
            programId = this[WorkoutSessionsTable.programId],
            workoutId = this[WorkoutSessionsTable.workoutId],
            workoutName = this[WorkoutSessionsTable.workoutName],
            startedAt = this[WorkoutSessionsTable.startedAt],
            finishedAt = this[WorkoutSessionsTable.finishedAt],
            sets = sets,
        )
    }

    private suspend fun <T> dbQuery(block: () -> T): AppResult<T> =
        withContext(Dispatchers.IO) {
            runCatching { transaction { block() } }.fold(
                onSuccess = { it.asSuccess() },
                onFailure = { AppError.Unexpected("Erro de banco", it).asFailure() },
            )
        }
}
