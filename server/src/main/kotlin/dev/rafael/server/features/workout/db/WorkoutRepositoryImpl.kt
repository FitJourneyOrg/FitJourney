package dev.rafael.server.features.workout.db

import dev.rafael.core.result.AppError
import dev.rafael.core.result.AppResult
import dev.rafael.core.result.asFailure
import dev.rafael.core.result.asSuccess
import dev.rafael.server.features.workout.models.Workout
import dev.rafael.server.features.workout.models.WorkoutExercise
import dev.rafael.server.features.workout.models.WorkoutSet
import dev.rafael.server.features.workout.models.WorkoutSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.time.Clock
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import kotlin.uuid.Uuid

class WorkoutRepositoryImpl : WorkoutRepository {

    private fun now(): LocalDateTime =
        Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())

    /**
     * Cria o treino. **IDEMPOTENTE** quando o cliente manda o id (ARCH #30, outbox).
     *
     * POR QUE aceitar id do cliente: uma fila com retry vai, cedo ou tarde, cair no caso em que
     * o POST chega, o servidor grava, e a RESPOSTA se perde. O worker vê falha e reenvia. Com id
     * gerado aqui, isso criaria um treino duplicado — não é hipótese, é garantido com volume.
     * Com id do cliente, o reenvio bate no mesmo id e não duplica.
     *
     * Mesmo padrão que a sessão já usa (`insertIgnore` = ON CONFLICT DO NOTHING). Se o insert
     * não pegou, o id já existe: devolve o que está lá, **filtrado por userId** — id vindo do
     * cliente não pode servir para tocar em treino alheio.
     *
     * `workout.id == Uuid.NIL` (o default do modelo) significa "gere você" — mantém o
     * comportamento antigo para quem ainda não manda id.
     */
    override suspend fun create(userId: Uuid, workout: Workout, programId: Uuid, dayOfWeek: Int): AppResult<Workout?> =
        dbQuery {
            val ts = now()
            val novoId = if (workout.id == Uuid.NIL) Uuid.random() else workout.id
            val inserted = WorkoutsTable.insertIgnore {
                it[id] = novoId
                it[WorkoutsTable.userId] = userId
                it[name] = workout.name
                it[WorkoutsTable.programId] = programId
                it[WorkoutsTable.dayOfWeek] = dayOfWeek
                it[createdAt] = ts
                it[updatedAt] = ts
            }
            if (inserted.insertedCount == 0) {
                // Reenvio: o treino já existe. Devolve o estado atual SEM regravar os filhos —
                // regravar duplicaria exercícios (mesmo cuidado da sessão).
                // null = o id existe mas é de OUTRO usuário → o service traduz em 409.
                readWorkout(userId, novoId)
            } else {
                insertChildren(novoId, workout.exercises)
                readWorkout(userId, novoId)!!
            }
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
            programId = wRow[WorkoutsTable.programId],
            dayOfWeek = wRow[WorkoutsTable.dayOfWeek],
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
            rir = this[WorkoutExercisesTable.rir],
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