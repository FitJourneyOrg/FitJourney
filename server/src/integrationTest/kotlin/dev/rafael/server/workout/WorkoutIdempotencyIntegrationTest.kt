package dev.rafael.server.workout

import dev.rafael.server.CodigoDeTeste

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import dev.rafael.core.result.AppResult
import dev.rafael.server.db.Migrations
import dev.rafael.server.features.exercise.db.ExercisesTable
import dev.rafael.server.features.program.models.ProgramsTable
import dev.rafael.server.features.user.db.UsersTable
import dev.rafael.server.features.workout.db.WorkoutExercisesTable
import dev.rafael.server.features.workout.db.WorkoutRepositoryImpl
import dev.rafael.server.features.workout.db.WorkoutsTable
import dev.rafael.server.features.workout.models.Workout
import dev.rafael.server.features.workout.models.WorkoutExercise
import dev.rafael.server.features.workout.models.WorkoutSet
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDateTime
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.testcontainers.postgresql.PostgreSQLContainer
import kotlin.uuid.Uuid

/**
 * Idempotência do POST /workouts com id gerado no CLIENTE (ARCH #30, outbox).
 *
 * POR QUE este teste existe: uma fila com retry vai, cedo ou tarde, cair no caso em que o POST
 * chega, o servidor grava, e a RESPOSTA se perde. O worker vê falha e reenvia. Sem id do
 * cliente isso criaria um treino duplicado — não é hipótese, é garantido com volume suficiente.
 *
 * Roda contra Postgres REAL: o comportamento depende do `insertIgnore` (ON CONFLICT DO NOTHING),
 * que é semântica do banco — um fake não provaria nada aqui.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class WorkoutIdempotencyIntegrationTest {

    private val postgres = PostgreSQLContainer("postgres:16-alpine")
    private lateinit var ds: HikariDataSource
    private val repo = WorkoutRepositoryImpl()

    private val dono = Uuid.random()
    private val outro = Uuid.random()
    private val programaId = Uuid.random()
    private lateinit var exercicioId: Uuid

    @BeforeAll
    fun setup() {
        postgres.start()
        ds = HikariConfig().apply {
            jdbcUrl = postgres.jdbcUrl
            username = postgres.username
            password = postgres.password
            driverClassName = "org.postgresql.Driver"
            isAutoCommit = false
        }.let(::HikariDataSource)
        Migrations.run(ds)
        Database.connect(ds)

        transaction {
            listOf(dono to "uid-dono", outro to "uid-outro").forEach { (id, uid) ->
                UsersTable.insert {
                    it[UsersTable.id] = id
                    it[firebaseUid] = uid
                    it[email] = "$uid@teste.local"
                    it[displayName] = uid   // V35: NOT NULL, CHECK de 2..30
                    it[code] = CodigoDeTeste.de(id)   // V40: NOT NULL + UNIQUE + CHECK
                }
            }
            val ts = LocalDateTime(2026, 8, 15, 10, 0)
            ProgramsTable.insert {
                it[id] = programaId
                it[userId] = dono
                it[name] = "Programa de teste"
                it[origin] = "MANUAL"
                it[daysPerWeek] = 3
                it[split] = "Manual"
                it[rationale] = ""
                it[createdAt] = ts
                it[updatedAt] = ts
                it[durationWeeks] = 8
                it[startedAt] = ts
            }
            // Exercício real do catálogo (FK de workout_exercises).
            exercicioId = ExercisesTable.selectAll().limit(1).single()[ExercisesTable.id]
        }
    }

    @AfterAll
    fun teardown() {
        ds.close()
        postgres.stop()
    }

    private fun treino(id: Uuid) = Workout(
        id = id,
        userId = Uuid.NIL,
        name = "Costas B",
        programId = null,
        dayOfWeek = null,
        exercises = listOf(
            WorkoutExercise(
                id = Uuid.NIL,
                exerciseId = exercicioId,
                orderIndex = 0,
                restSeconds = 90,
                rir = 2,
                sets = listOf(WorkoutSet(Uuid.NIL, reps = 10, orderIndex = 0)),
            ),
        ),
        createdAt = LocalDateTime(2026, 8, 15, 10, 0),
        updatedAt = LocalDateTime(2026, 8, 15, 10, 0),
    )

    private fun contarTreinos(id: Uuid) = transaction {
        WorkoutsTable.selectAll().where { WorkoutsTable.id eq id }.count()
    }

    private fun contarExercicios(id: Uuid) = transaction {
        WorkoutExercisesTable.selectAll().where { WorkoutExercisesTable.workoutId eq id }.count()
    }

    @Test
    fun `reenviar o mesmo id nao duplica o treino nem os exercicios`() = runBlocking {
        val id = Uuid.random()

        val primeira = repo.create(dono, treino(id), programaId, dayOfWeek = 2)
        val segunda = repo.create(dono, treino(id), programaId, dayOfWeek = 2)

        assertNotNull((primeira as AppResult.Success).value)
        assertNotNull((segunda as AppResult.Success).value, "reenvio deve devolver o treino existente")

        assertEquals(1L, contarTreinos(id), "o reenvio criou um treino duplicado")
        // O detalhe que morde: sem a guarda, o 2º insert recriaria os filhos e o treino
        // apareceria com o dobro de exercícios — sem violar nenhuma constraint.
        assertEquals(1L, contarExercicios(id), "o reenvio duplicou os exercícios")
    }

    @Test
    fun `id de outro usuario nao permite tocar no treino alheio`() = runBlocking {
        val id = Uuid.random()
        repo.create(dono, treino(id), programaId, dayOfWeek = 3)

        // `outro` tenta criar com um id que já é do `dono`. Como o id vem do CLIENTE, isto é
        // uma tentativa plausível — e não pode virar sequestro de recurso.
        val resultado = repo.create(outro, treino(id), programaId, dayOfWeek = 3)

        assertNull(
            (resultado as AppResult.Success).value,
            "id de outro dono deve devolver null (o service traduz em 409)",
        )
        assertEquals(1L, contarTreinos(id))
        // O treino continua sendo do dono original.
        val donoNoBanco = transaction {
            WorkoutsTable.selectAll().where { WorkoutsTable.id eq id }.single()[WorkoutsTable.userId]
        }
        assertEquals(dono, donoNoBanco, "o treino trocou de dono")
    }

    @Test
    fun `sem id o servidor gera (comportamento antigo intacto)`() = runBlocking {
        val semId = treino(Uuid.NIL)

        val a = (repo.create(dono, semId, programaId, dayOfWeek = 4) as AppResult.Success).value
        val b = (repo.create(dono, semId, programaId, dayOfWeek = 5) as AppResult.Success).value

        assertNotNull(a)
        assertNotNull(b)
        // Uuid.NIL significa "gere você" — duas chamadas iguais criam DOIS treinos, que é o
        // comportamento de quem ainda não passa pelo outbox.
        assertNotEquals(a!!.id, b!!.id, "sem id do cliente, cada POST deve criar um treino novo")
    }
}
