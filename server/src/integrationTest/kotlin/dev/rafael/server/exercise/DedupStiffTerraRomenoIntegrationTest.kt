package dev.rafael.server.exercise

import dev.rafael.server.BancoDeTeste
import dev.rafael.server.features.exercise.db.ExercisesTable
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

/**
 * Trava o veredito da **V63** (dedup funcional stiff x terra romeno, débito C4): o Stiff fica,
 * os dois Terra Romeno somem.
 *
 * ## O caminho de falha que importa
 *
 * Apagar o `Levantamento Terra Romeno` sozinho tiraria do catálogo o ÚNICO hinge alcançável com
 * halteres (o `Stiff com Halteres` estava sem padrão e fora da base, então o `SlotFiller` nunca o
 * sorteava). Quem treina em casa só com halteres ficaria sem posterior no programa, e nenhum erro
 * apareceria: o motor simplesmente escolheria outra coisa. É esse buraco que o último teste fecha.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DedupStiffTerraRomenoIntegrationTest {

    @BeforeAll
    fun setup() {
        BancoDeTeste.dataSource
        BancoDeTeste.limpar()
    }

    private fun linha(nome: String) = transaction {
        ExercisesTable.selectAll().firstOrNull { it[ExercisesTable.name] == nome }
    }

    @Test
    fun `os dois Terra Romeno sumiram e os dois Stiff ficaram`() {
        assertEquals(null, linha("Levantamento Terra Romeno"))
        assertEquals(null, linha("Levantamento Terra Romeno com Halteres"))
        assertNotNull(linha("Stiff com Barra"))
        assertNotNull(linha("Stiff com Halteres"))
    }

    @Test
    fun `Stiff com Halteres herdou a taxonomia de hinge e continua de halteres`() {
        val stiff = linha("Stiff com Halteres")!!
        assertEquals("HINGE", stiff[ExercisesTable.movementPattern])
        assertTrue(stiff[ExercisesTable.isBase], "base: o motor escolhe base antes de variação")
        assertTrue("LEGS" in stiff[ExercisesTable.primaryMuscles].orEmpty())
        assertEquals("DUMBBELL", stiff[ExercisesTable.equipment])
    }

    @Test
    fun `existe hinge alcancavel pelo motor para quem so tem halteres`() {
        val alcancaveis = transaction {
            ExercisesTable.selectAll().filter {
                it[ExercisesTable.equipment] == "DUMBBELL" &&
                    it[ExercisesTable.movementPattern] == "HINGE" &&
                    "LEGS" in it[ExercisesTable.primaryMuscles].orEmpty()
            }.map { it[ExercisesTable.name] }
        }
        assertFalse(alcancaveis.isEmpty(), "sem hinge de halteres o usuário de casa fica sem posterior")
    }
}
