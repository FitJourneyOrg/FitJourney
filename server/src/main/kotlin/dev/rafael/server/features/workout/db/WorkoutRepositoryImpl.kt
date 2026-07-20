package dev.rafael.server.features.workout.db

import dev.rafael.core.result.AppError
import dev.rafael.core.result.AppResult
import dev.rafael.core.result.asFailure
import dev.rafael.core.result.asSuccess
import dev.rafael.server.features.exercise.db.ExercisesTable
import dev.rafael.server.features.workout.db.WorkoutExercisesTable.restSeconds
import dev.rafael.server.features.workout.models.Workout
import dev.rafael.server.features.workout.models.WorkoutExercise
import dev.rafael.server.features.workout.models.WorkoutSet
import dev.rafael.server.features.workout.models.WorkoutSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import kotlin.uuid.Uuid

class WorkoutRepositoryImpl : WorkoutRepository {

    private fun now(): LocalDateTime =
        Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())

    override suspend fun create(userId: Uuid, workout: Workout): AppResult<Workout> =
        dbQuery {
            val ts = now()
            val newWorkoutId = Uuid.random()
            WorkoutsTable.insert {
                it[id] = newWorkoutId
                it[WorkoutsTable.userId] = userId
                it[name] = workout.name
                it[createdAt] = ts
                it[updatedAt] = ts
            }
            insertChildren(newWorkoutId, workout.exercises)
            readWorkout(userId, newWorkoutId)!!
        }

    override suspend fun findAllByUser(userId: Uuid): AppResult<List<WorkoutSummary>> =
        dbQuery {
            WorkoutsTable.selectAll()
                .where { WorkoutsTable.userId eq userId }
                .map { row ->
                    val wId = row[WorkoutsTable.id]
                    val count = WorkoutExercisesTable.selectAll()
                        .where { WorkoutExercisesTable.workoutId eq wId }
                        .count().toInt()
                    WorkoutSummary(
                        id = wId,
                        name = row[WorkoutsTable.name],
                        exerciseCount = count,
                        updatedAt = row[WorkoutsTable.updatedAt],
                    )
                }
        }

    override suspend fun findById(userId: Uuid, workoutId: Uuid): AppResult<Workout?> =
        dbQuery { readWorkout(userId, workoutId) }

    override suspend fun update(userId: Uuid, workoutId: Uuid, workout: Workout): AppResult<Workout?> =
        dbQuery {
            // confirma posse
            val owns = WorkoutsTable.selectAll()
                .where { (WorkoutsTable.id eq workoutId) and (WorkoutsTable.userId eq userId) }
                .any()
            if (!owns) return@dbQuery null

            // substitui: apaga filhos e recria (CASCADE apaga sets junto dos exercises)
            WorkoutExercisesTable.deleteWhere { WorkoutExercisesTable.workoutId eq workoutId }
            WorkoutsTable.update({ WorkoutsTable.id eq workoutId }) {
                it[name] = workout.name
                it[updatedAt] = now()
            }
            insertChildren(workoutId, workout.exercises)
            readWorkout(userId, workoutId)
        }

    override suspend fun delete(userId: Uuid, workoutId: Uuid): AppResult<Boolean> =
        dbQuery {
            val n = WorkoutsTable.deleteWhere {
                (WorkoutsTable.id eq workoutId) and (WorkoutsTable.userId eq userId)
            }
            n > 0
        }

    // ---- helpers (dentro da transação) ----

    private fun insertChildren(workoutId: Uuid, exercises: List<WorkoutExercise>) {
        exercises.forEach { ex ->
            val weId = Uuid.random()
            WorkoutExercisesTable.insert {
                it[id] = weId
                it[WorkoutExercisesTable.workoutId] = workoutId
                it[exerciseId] = ex.exerciseId
                it[orderIndex] = ex.orderIndex
                it[restSeconds] = ex.restSeconds
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

    /** Lê o aggregate por queries separadas (N+1 contido: 1 treino pequeno). */
    private fun readWorkout(userId: Uuid, workoutId: Uuid): Workout? {
        val wRow = WorkoutsTable.selectAll()
            .where { (WorkoutsTable.id eq workoutId) and (WorkoutsTable.userId eq userId) }
            .singleOrNull() ?: return null

        val exercises = WorkoutExercisesTable.selectAll()
            .where { WorkoutExercisesTable.workoutId eq workoutId }
            .map { it.toWorkoutExercise() }
            .sortedBy { it.orderIndex }

        return Workout(
            id = wRow[WorkoutsTable.id],
            userId = wRow[WorkoutsTable.userId],
            name = wRow[WorkoutsTable.name],
            exercises = exercises,
            createdAt = wRow[WorkoutsTable.createdAt],
            updatedAt = wRow[WorkoutsTable.updatedAt],
        )
    }

    private fun ResultRow.toWorkoutExercise(): WorkoutExercise {
        val weId = this[WorkoutExercisesTable.id]
        val sets = WorkoutSetsTable.selectAll()
            .where { WorkoutSetsTable.workoutExerciseId eq weId }
            .map { WorkoutSet(it[WorkoutSetsTable.id], it[WorkoutSetsTable.reps], it[WorkoutSetsTable.orderIndex]) }
            .sortedBy { it.orderIndex }
        return WorkoutExercise(
            id = weId,
            exerciseId = this[WorkoutExercisesTable.exerciseId],
            orderIndex = this[WorkoutExercisesTable.orderIndex],
            restSeconds = this[WorkoutExercisesTable.restSeconds],
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