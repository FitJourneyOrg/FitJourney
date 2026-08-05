package dev.rafael.server.engine

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import dev.rafael.contract.profile.Goal
import dev.rafael.contract.profile.Level
import dev.rafael.contract.profile.MuscleGroup
import dev.rafael.contract.profile.ProfileDto
import dev.rafael.contract.profile.TrainingEnvironment
import dev.rafael.server.db.Migrations
import dev.rafael.server.features.exercise.db.ExercisesTable
import dev.rafael.server.features.exercise.engine.DeterministicWorkoutGenerator
import dev.rafael.server.features.exercise.engine.ExercisePreFilter
import dev.rafael.server.features.exercise.engine.StructureEngine
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.testcontainers.postgresql.PostgreSQLContainer
import kotlin.uuid.Uuid

/**
 * Harness de geração: roda o MOTOR REAL (StructureEngine + ExercisePreFilter + SlotFiller)
 * contra um Postgres com o catálogo seedado (Testcontainers + Flyway), a partir de um
 * "questionário do onboarding" simulado (ProfileDto). Imprime o treino gerado no console.
 *
 * Objetivo: testar a geração SEM abrir o emulador. Edite `onboarding()` e rode:
 *   ./gradlew :server:integrationTest --tests "*WorkoutGenerationIntegrationTest*"
 * (precisa de Docker rodando, por causa do Testcontainers).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class WorkoutGenerationIntegrationTest {

    private val postgres = PostgreSQLContainer("postgres:16-alpine")
    private lateinit var ds: HikariDataSource
    private val generator by lazy { DeterministicWorkoutGenerator(StructureEngine(), ExercisePreFilter()) }

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
        Migrations.run(ds)     // aplica todas as migrations = seed do catálogo (V4/V9/V14/V15)
        Database.connect(ds)   // Exposed conecta (o ExercisePreFilter usa transaction {})
    }

    @AfterAll
    fun teardown() {
        ds.close()
        postgres.stop()
    }

    // ========================= EDITE AQUI o "questionário do onboarding" =========================
    private fun onboarding(): ProfileDto = ProfileDto(
        goal = Goal.GAIN_MUSCLE,                                    // GAIN_MUSCLE | LOSE_FAT | MAINTAIN | GENERAL_HEALTH
        level = Level.INTERMEDIATE,                                 // BEGINNER | INTERMEDIATE | ADVANCED
        daysPerWeek = 3,                                            // 2..6
        focusAreas = listOf(MuscleGroup.CHEST, MuscleGroup.BICEPS),  // grupos priorizados (INTER/ADV, máx 2)
        environment = TrainingEnvironment.CASA,                // ACADEMIA | CASA
        weightKg = 78.0,
        heightCm = 178.0,
        onboardingCompleted = true,
    )
    // =============================================================================================

    @Test
    fun `gera treino a partir do onboarding e imprime`() = runBlocking {
        val profile = onboarding()
        val program = generator.generate(profile, prompt = null)

        // resolve os nomes do catálogo pelos ids gerados
        val ids = program.workouts.flatMap { w -> w.exercises.map { Uuid.parse(it.exerciseId) } }
        val names: Map<String, String> = transaction {
            ExercisesTable.selectAll()
                .where { ExercisesTable.id inList ids }
                .associate { it[ExercisesTable.id].toString() to it[ExercisesTable.name] }
        }

        // ---- imprime o treino gerado ----
        println("\n================ PROGRAMA GERADO ================")
        println("Perfil: ${profile.level} · ${profile.goal} · ${profile.daysPerWeek} dias · " +
            "foco=${profile.focusAreas} · ${profile.environment}")
        println("Split: ${program.split}")
        println("Rationale: ${program.rationale}\n")
        program.workouts.forEach { w ->
            val totalSets = w.exercises.sumOf { it.sets.size }
            println("--- ${w.name}  (${w.exercises.size} exercícios · $totalSets séries) ---")
            w.exercises.sortedBy { it.orderIndex }.forEach { ex ->
                val nome = names[ex.exerciseId] ?: "??? id=${ex.exerciseId}"
                val reps = ex.sets.firstOrNull()?.reps ?: 0
                println("  • $nome — ${ex.sets.size}x$reps · descanso ${ex.restSeconds}s · RIR ${ex.rir}")
            }
            println()
        }
        println("================================================\n")

        // ---- asserts de sanidade (o motor não gerou lixo) ----
        assertTrue(program.workouts.isNotEmpty(), "o programa deve ter treinos")
        assertTrue(program.workouts.all { it.exercises.isNotEmpty() }, "todo treino deve ter exercícios")
        assertTrue(program.workouts.all { it.exercises.size <= 6 }, "máximo 6 exercícios por sessão")
        assertTrue(
            program.workouts.all { w -> w.exercises.sumOf { it.sets.size } <= 20 },
            "máximo 20 séries por sessão (teto de volume)",
        )
        assertTrue(
            ids.distinct().all { names.containsKey(it.toString()) },
            "todo exercício gerado deve existir no catálogo (nome resolvido)",
        )
    }
}
