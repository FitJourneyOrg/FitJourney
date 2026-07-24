package dev.rafael.server.features.program.db

import dev.rafael.contract.workout.WorkoutOrigin
import dev.rafael.core.result.AppError
import dev.rafael.core.result.AppResult
import dev.rafael.core.result.asFailure
import dev.rafael.core.result.asSuccess
import dev.rafael.server.features.program.models.Program
import dev.rafael.server.features.program.models.ProgramCounts
import dev.rafael.server.features.program.models.ProgramsTable
import dev.rafael.server.features.workout.db.WorkoutExercisesTable
import dev.rafael.server.features.workout.db.WorkoutSetsTable
import dev.rafael.server.features.workout.db.WorkoutsTable
import dev.rafael.server.features.workout.models.Workout
import dev.rafael.server.features.workout.models.WorkoutExercise
import dev.rafael.server.features.workout.models.WorkoutSet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import kotlin.uuid.Uuid

/**
 * Persistência do programa (ARCH #26 — multi-programa, substitui o modelo "1 ativo"
 * da G.1). Segue o padrão do WorkoutRepositoryImpl (dbQuery → AppResult). Reusa a
 * MESMA lógica de inserir exercises+sets pra evitar drift do restSeconds.
 */
class ProgramRepositoryImpl : ProgramRepository {

    private fun now(): LocalDateTime =
        Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())

    override suspend fun counts(userId: Uuid): AppResult<ProgramCounts> = dbQuery {
        var ai = 0
        var manual = 0
        ProgramsTable.selectAll()
            .where { ProgramsTable.userId eq userId }
            .forEach { row ->
                when (row[ProgramsTable.origin]) {
                    WorkoutOrigin.AI.name -> ai++
                    WorkoutOrigin.MANUAL.name -> manual++
                }
            }
        ProgramCounts(ai = ai, manual = manual)
    }

    override suspend fun findAllByUser(userId: Uuid): AppResult<List<Program>> = dbQuery {
        ProgramsTable.selectAll()
            .where { ProgramsTable.userId eq userId }
            .orderBy(ProgramsTable.createdAt, SortOrder.DESC)
            .map { row ->
                val programId = row[ProgramsTable.id]
                row.toProgram(readProgramWorkouts(userId, programId))
            }
    }

    override suspend fun findByIdForUser(userId: Uuid, programId: Uuid): AppResult<Program?> = dbQuery {
        val row = ProgramsTable.selectAll()
            .where { (ProgramsTable.id eq programId) and (ProgramsTable.userId eq userId) }
            .limit(1)
            .singleOrNull() ?: return@dbQuery null
        row.toProgram(readProgramWorkouts(userId, programId))
    }

    override suspend fun rename(userId: Uuid, programId: Uuid, name: String): AppResult<Program?> = dbQuery {
        val updated = ProgramsTable.update({ (ProgramsTable.id eq programId) and (ProgramsTable.userId eq userId) }) {
            it[ProgramsTable.name] = name
            it[updatedAt] = now()
        }
        if (updated == 0) return@dbQuery null
        val row = ProgramsTable.selectAll().where { ProgramsTable.id eq programId }.single()
        row.toProgram(readProgramWorkouts(userId, programId))
    }

    override suspend fun delete(userId: Uuid, programId: Uuid): AppResult<Boolean> = dbQuery {
        // CASCADE (V11: workouts.program_id ON DELETE CASCADE) apaga os treinos do programa.
        val n = ProgramsTable.deleteWhere {
            (ProgramsTable.id eq programId) and (ProgramsTable.userId eq userId)
        }
        n > 0
    }

    override suspend fun createForUser(userId: Uuid, program: Program): AppResult<Program> = dbQuery {
        val ts = now()
        val programId = Uuid.random()
        ProgramsTable.insert {
            it[id] = programId
            it[this.userId] = userId
            it[name] = program.name
            it[origin] = program.origin.name
            it[daysPerWeek] = program.daysPerWeek
            it[split] = program.split
            it[rationale] = program.rationale
            it[locked] = program.locked
            it[createdAt] = ts
            it[updatedAt] = ts
        }

        val savedWorkouts = program.workouts.mapIndexed { index, w ->
            val workoutId = Uuid.random()
            WorkoutsTable.insert {
                it[id] = workoutId
                it[WorkoutsTable.userId] = userId
                it[name] = w.name
                it[WorkoutsTable.programId] = programId
                it[dayOfWeek] = index + 1                 // 1..N (usuário reordena na G.2)
                it[createdAt] = ts
                it[updatedAt] = ts
            }
            insertChildren(workoutId, w.exercises)
            w.copy(id = workoutId)
        }

        program.copy(id = programId, workouts = savedWorkouts, createdAt = ts, updatedAt = ts)
    }

    // ---- helpers (dentro da transação) — MESMA lógica do WorkoutRepositoryImpl ----

    private fun insertChildren(workoutId: Uuid, exercises: List<WorkoutExercise>) {
        exercises.forEach { ex ->
            val weId = Uuid.random()
            WorkoutExercisesTable.insert {
                it[id] = weId
                it[WorkoutExercisesTable.workoutId] = workoutId
                it[exerciseId] = ex.exerciseId
                it[orderIndex] = ex.orderIndex
                it[restSeconds] = ex.restSeconds
                it[rir] = ex.rir
            }
            ex.sets.forEach { s ->
                WorkoutSetsTable.insert {
                    it[id] = Uuid.random()
                    it[workoutExerciseId] = weId
                    it[reps] = s.reps
                    it[orderIndex] = s.orderIndex
                }
            }
        }
    }

    private fun readProgramWorkouts(userId: Uuid, programId: Uuid): List<Workout> {
        return WorkoutsTable.selectAll()
            .where { WorkoutsTable.programId eq programId }
            .map { wRow ->
                val workoutId = wRow[WorkoutsTable.id]
                val exercises = WorkoutExercisesTable.selectAll()
                    .where { WorkoutExercisesTable.workoutId eq workoutId }
                    .map { it.toWorkoutExercise() }
                    .sortedBy { it.orderIndex }
                Workout(
                    id = workoutId,
                    userId = userId,
                    programId = programId,
                    name = wRow[WorkoutsTable.name],
                    exercises = exercises,
                    createdAt = wRow[WorkoutsTable.createdAt],
                    updatedAt = wRow[WorkoutsTable.updatedAt],
                )
            }
            .sortedBy { it.createdAt }
    }

    private fun ResultRow.toWorkoutExercise(): WorkoutExercise {
        val weId = this[WorkoutExercisesTable.id]
        val sets = WorkoutSetsTable.selectAll()
            .where { WorkoutSetsTable.workoutExerciseId eq weId }
            .map {
                WorkoutSet(
                    it[WorkoutSetsTable.id], it[WorkoutSetsTable.reps], it[WorkoutSetsTable.orderIndex]
                )
            }
            .sortedBy { it.orderIndex }
        return WorkoutExercise(
            id = weId,
            exerciseId = this[WorkoutExercisesTable.exerciseId],
            orderIndex = this[WorkoutExercisesTable.orderIndex],
            restSeconds = this[WorkoutExercisesTable.restSeconds],
            rir = this[WorkoutExercisesTable.rir],
            sets = sets,
        )
    }

    private fun ResultRow.toProgram(workouts: List<Workout>) = Program(
        id = this[ProgramsTable.id],
        userId = this[ProgramsTable.userId],
        name = this[ProgramsTable.name],
        origin = WorkoutOrigin.valueOf(this[ProgramsTable.origin]),
        daysPerWeek = this[ProgramsTable.daysPerWeek],
        split = this[ProgramsTable.split],
        rationale = this[ProgramsTable.rationale],
        locked = this[ProgramsTable.locked],
        workouts = workouts,
        createdAt = this[ProgramsTable.createdAt],
        updatedAt = this[ProgramsTable.updatedAt],
    )

    private suspend fun <T> dbQuery(block: () -> T): AppResult<T> =
        withContext(Dispatchers.IO) {
            runCatching { transaction { block() } }.fold(
                onSuccess = { it.asSuccess() },
                onFailure = { AppError.Unexpected("Erro de banco", it).asFailure() },
            )
        }
}
