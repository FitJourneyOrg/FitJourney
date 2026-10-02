package dev.rafael.server.features.stats

import dev.rafael.contract.profile.MuscleGroup
import dev.rafael.server.features.stats.ProgressPolicy.SerieFeita
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/**
 * Prova a analise de progressao (J.2): o corte de carga externa, as semanas que precisam
 * aparecer vazias, as que NAO podem virar zero, e a comparacao que se recusa a comparar
 * treinos diferentes.
 */
class ProgressPolicyTest {

    private fun id(n: Int) = Uuid.parse("0000000$n-0000-0000-0000-000000000000")
    private val agachamento = id(1)
    private val supino = id(2)
    private val prancha = id(3)

    private fun serie(
        dia: String, kg: Double, reps: Int = 10,
        ex: Uuid = agachamento, treino: String = "Inferior A", sessao: Uuid = id(9),
    ) = SerieFeita(sessao, LocalDate.parse(dia), treino, ex, reps, kg)

    // ---- elegibilidade -----------------------------------------------------

    @Test
    fun `serie com carga externa entra na analise`() {
        assertTrue(ProgressPolicy.elegivel(done = true, weightKg = 60.0))
    }

    @Test
    fun `peso corporal fica de fora - weightKg nulo e calistenia`() {
        assertTrue(!ProgressPolicy.elegivel(done = true, weightKg = null))
    }

    @Test
    fun `serie nao marcada como feita nao conta, mesmo com carga`() {
        assertTrue(!ProgressPolicy.elegivel(done = false, weightKg = 80.0))
    }

    @Test
    fun `peso zero e tratado como ausencia de carga`() {
        assertTrue(!ProgressPolicy.elegivel(done = true, weightKg = 0.0))
    }

    // ---- tonelagem ---------------------------------------------------------

    @Test
    fun `tonelagem soma reps vezes carga de cada serie`() {
        val s = listOf(serie("2026-09-28", 60.0, reps = 8), serie("2026-09-28", 60.0, reps = 8))
        assertEquals(960.0, ProgressPolicy.tonelagem(s))
    }

    @Test
    fun `historico so de calistenia da tonelagem zero sem estourar`() {
        assertEquals(0.0, ProgressPolicy.tonelagem(emptyList()))
    }

    // ---- semanas -----------------------------------------------------------

    @Test
    fun `semana comeca na segunda, venha o treino de que dia vier`() {
        // domingo 2026-09-27 pertence a semana que comeca em 2026-09-21
        assertEquals(LocalDate.parse("2026-09-21"), ProgressPolicy.segundaDaSemana(LocalDate.parse("2026-09-27")))
        assertEquals(LocalDate.parse("2026-09-21"), ProgressPolicy.segundaDaSemana(LocalDate.parse("2026-09-21")))
    }

    @Test
    fun `semana sem treino aparece com zero, nao some da serie`() {
        val hoje = LocalDate.parse("2026-10-01")
        // treinou so na semana atual; as 3 anteriores ficaram vazias
        val semanas = ProgressPolicy.porSemana(listOf(serie("2026-09-28", 50.0)), hoje, semanas = 4)
        assertEquals(4, semanas.size)
        assertEquals(listOf(0.0, 0.0, 0.0, 500.0), semanas.map { it.kg })
        assertEquals(LocalDate.parse("2026-09-28"), semanas.last().semana)
    }

    @Test
    fun `treino anterior a janela nao entra na contagem`() {
        val hoje = LocalDate.parse("2026-10-01")
        val antigo = serie("2026-01-05", 100.0)
        assertEquals(0.0, ProgressPolicy.porSemana(listOf(antigo), hoje, semanas = 4).sumOf { it.kg })
    }

    // ---- carga estimada ----------------------------------------------------

    @Test
    fun `Epley desmente o aumento de carga que custou repeticoes`() {
        // 3x8 a 67,5 kg "pesa mais" que 3x10 a 65 kg, e estima menos: 85,5 contra 86,7
        assertTrue(ProgressPolicy.e1rm(67.5, 8) < ProgressPolicy.e1rm(65.0, 10))
    }

    @Test
    fun `Epley reconhece progresso feito so de repeticao, com a mesma carga`() {
        assertTrue(ProgressPolicy.e1rm(65.0, 12) > ProgressPolicy.e1rm(65.0, 10))
    }

    @Test
    fun `serie com zero repeticoes nao estima carga - evita divisao sem sentido`() {
        assertEquals(0.0, ProgressPolicy.e1rm(100.0, 0))
    }

    @Test
    fun `evolucao pula a semana em que o exercicio nao foi treinado`() {
        val s = listOf(
            serie("2026-09-14", 60.0, reps = 8),
            serie("2026-09-28", 65.0, reps = 8), // pulou a semana de 21/09
        )
        val pontos = ProgressPolicy.evolucao(s, agachamento)
        assertEquals(2, pontos.size)
        assertTrue(pontos.none { it.e1rm == 0.0 })
        assertTrue(pontos[0].e1rm < pontos[1].e1rm)
    }

    @Test
    fun `evolucao de exercicio nunca treinado vem vazia`() {
        assertEquals(emptyList(), ProgressPolicy.evolucao(listOf(serie("2026-09-28", 60.0)), supino))
    }

    @Test
    fun `mais relevantes ordena pelo que realmente pesou`() {
        val s = listOf(
            serie("2026-09-28", 100.0, reps = 10, ex = agachamento),
            serie("2026-09-28", 20.0, reps = 10, ex = supino),
        )
        assertEquals(listOf(agachamento, supino), ProgressPolicy.maisRelevantes(s, quantos = 2))
    }

    // ---- volume por grupo --------------------------------------------------

    @Test
    fun `volume e contado em series e uma serie conta para cada primario`() {
        val s = List(4) { serie("2026-09-28", 60.0, ex = agachamento) }
        val mapa = mapOf(agachamento to listOf(MuscleGroup.LEGS, MuscleGroup.GLUTES))
        val v = ProgressPolicy.seriesPorGrupo(s, mapa, semanas = 2)
        assertEquals(2.0, v.porGrupo[MuscleGroup.LEGS])    // 4 series / 2 semanas
        assertEquals(2.0, v.porGrupo[MuscleGroup.GLUTES])
        assertEquals(0.0, v.semClassificacao)
    }

    @Test
    fun `exercicio sem primary_muscles no catalogo vira nao classificado, nao some`() {
        val s = listOf(serie("2026-09-28", 60.0, ex = prancha))
        val v = ProgressPolicy.seriesPorGrupo(s, musculos = emptyMap(), semanas = 1)
        assertTrue(v.porGrupo.isEmpty())
        assertEquals(1.0, v.semClassificacao)
    }

    // ---- ultimo vs anterior ------------------------------------------------

    @Test
    fun `compara a sessao mais recente com a anterior do MESMO treino`() {
        val s = listOf(
            serie("2026-09-21", 60.0, reps = 10, sessao = id(1), treino = "Inferior A"),
            serie("2026-09-23", 40.0, reps = 10, sessao = id(2), treino = "Superior A", ex = supino),
            serie("2026-09-28", 65.0, reps = 10, sessao = id(3), treino = "Inferior A"),
        )
        val c = ProgressPolicy.ultimoVsAnterior(s)!!
        assertEquals("Inferior A", c.nomeDoTreino)
        assertEquals(LocalDate.parse("2026-09-21"), c.dataAnterior)
        assertEquals(650.0, c.kgAtual)
        assertEquals(600.0, c.kgAnterior)
        assertEquals(50.0, c.deltaPorExercicio[agachamento])
    }

    @Test
    fun `sem par do mesmo treino nao ha comparacao - devolve nulo`() {
        val s = listOf(
            serie("2026-09-28", 65.0, sessao = id(1), treino = "Inferior A"),
            serie("2026-09-23", 40.0, sessao = id(2), treino = "Superior A", ex = supino),
        )
        assertNull(ProgressPolicy.ultimoVsAnterior(s))
    }

    @Test
    fun `historico vazio nao compara nada`() {
        assertNull(ProgressPolicy.ultimoVsAnterior(emptyList()))
    }
}
