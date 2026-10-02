package dev.rafael.features.session.presentation.state

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * O agrupamento por exercício é DERIVADO da lista plana de séries — não existe segunda lista.
 * Este teste é Tier 1: não precisa de ViewModel nenhum, só do state.
 */
class AgrupamentoDaSessaoTest {

    private fun serie(ordem: Int, set: Int, nome: String, feita: Boolean = false) = SetEntry(
        exerciseId = "ex-$ordem", exerciseName = nome, orderIndex = ordem, setIndex = set,
        targetReps = 10, repsDone = "10", weight = "", done = feita, restSeconds = 90,
    )

    /** Fora de ordem de propósito: ordenar é responsabilidade da projeção, não da entrada. */
    private val estado = WorkoutSessionState(
        isLoading = false,
        entries = listOf(
            serie(1, 1, "Remada"),
            serie(0, 0, "Supino", feita = true),
            serie(1, 0, "Remada"),
            serie(0, 1, "Supino"),
        ),
    )

    @Test
    fun `agrupa por exercicio e ordena as series dentro dele`() {
        val exercicios = estado.exercicios

        assertEquals(2, exercicios.size)
        assertEquals(listOf("Supino", "Remada"), exercicios.map { it.nome })
        assertEquals(listOf(0, 1), exercicios[0].series.map { it.entrada.setIndex })
    }

    @Test
    fun `cada serie guarda o indice dela na lista plana`() {
        // É esse índice que os eventos usam -- se a projeção o perder, marcar a série do segundo
        // exercício gravaria na do primeiro, em silêncio.
        assertEquals(listOf(1, 3), estado.exercicios[0].series.map { it.indice })
        assertEquals(listOf(2, 0), estado.exercicios[1].series.map { it.indice })
    }

    @Test
    fun `exercicio so esta concluido com todas as series feitas`() {
        assertFalse(estado.exercicios[0].concluido, "uma das duas séries não foi feita")

        val todasFeitas = estado.copy(entries = estado.entries.map { it.copy(done = true) })
        assertTrue(todasFeitas.exercicios.all { it.concluido })
    }

    @Test
    fun `navegacao conhece as bordas`() {
        assertFalse(estado.temAnterior, "está no primeiro e ofereceu voltar")
        assertTrue(estado.temProximo)

        val noUltimo = estado.copy(exercicioAtual = 1)
        assertTrue(noUltimo.temAnterior)
        assertFalse(noUltimo.temProximo, "está no último e ofereceu avançar")
    }

    @Test
    fun `lista vazia nao quebra a projecao`() {
        // Caminho de falha: o state nasce vazio (isLoading) e a tela lê `exercicio` nesse instante.
        val vazio = WorkoutSessionState()
        assertEquals(emptyList(), vazio.exercicios)
        assertEquals(null, vazio.exercicio)
        assertEquals(null, vazio.serie)
        assertFalse(vazio.temProximo)
    }
}
