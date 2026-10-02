package dev.rafael.features.exercise.data

import dev.rafael.contract.exercise.ExerciseCategory
import dev.rafael.features.exercise.domain.model.Exercise
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A busca é pura e por isso testável sozinha — Tier 1. O que ela protege não é "achar": é **achar
 * do jeito que se digita no celular**, sem acento e sem acertar a ordem das palavras.
 */
class BuscaDeExercicioTest {

    private fun exercicio(nome: String) = Exercise(
        id = nome, name = nome, category = ExerciseCategory.LEGS,
        description = null, videoRef = "v.mp4", thumbRef = "t.png",
        primaryMuscles = emptyList(), secondaryMuscles = emptyList(),
        equipment = null, movementPattern = null,
        isCompound = null, unilateral = null, prescriptionType = null, level = null,
    )

    private val acervo = listOf(
        exercicio("Abdução de Quadril em Pé na Máquina"),
        exercicio("Agachamento Búlgaro com Salto"),
        exercicio("Rosca Direta com Barra"),
        exercicio("Tríceps Francês"),
    )

    private fun buscar(termo: String) = acervo.filtradosPorBusca(termo).map { it.name }

    @Test
    fun `sem acento acha com acento`() {
        assertEquals(listOf("Abdução de Quadril em Pé na Máquina"), buscar("abducao"))
        assertEquals(listOf("Tríceps Francês"), buscar("triceps frances"))
    }

    @Test
    fun `caixa alta ou baixa da no mesmo`() {
        assertEquals(buscar("rosca"), buscar("ROSCA"))
        assertEquals(listOf("Rosca Direta com Barra"), buscar("ROSCA"))
    }

    @Test
    fun `a ordem das palavras nao importa`() {
        val esperado = listOf("Agachamento Búlgaro com Salto")
        assertEquals(esperado, buscar("agach bulgaro"))
        assertEquals(esperado, buscar("bulgaro agach"))
    }

    @Test
    fun `termo em branco devolve o acervo inteiro`() {
        assertEquals(acervo.size, acervo.filtradosPorBusca("").size)
        assertEquals(acervo.size, acervo.filtradosPorBusca("   ").size, "só espaço não é filtro")
    }

    @Test
    fun `palavra que nao existe devolve vazio, e nao o acervo`() {
        assertTrue(buscar("supino").isEmpty())
        // Caminho de falha do token: uma palavra confere e a outra não -> não entra.
        assertTrue(buscar("agachamento supino").isEmpty())
    }

    /**
     * ⚠️ Invariante da tabela de acentos: as duas listas são lidas por ÍNDICE. Um caractere
     * acrescentado só numa delas não quebraria compilação nenhuma — trocaria um acento por outro,
     * em silêncio, e só apareceria como "a busca às vezes erra".
     */
    @Test
    fun `a tabela de acentos tem os dois lados do mesmo tamanho`() {
        assertEquals(ACENTUADOS.length, SEM_ACENTO.length)
        assertTrue(SEM_ACENTO.all { it.code < 128 }, "o lado de destino tem de ser ASCII puro")
    }
}
