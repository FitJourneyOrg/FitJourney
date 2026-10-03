package dev.rafael.server.features.stats

import dev.rafael.contract.profile.MuscleGroup
import dev.rafael.server.features.stats.ProgressPolicy.SerieFeita
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
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

    // ---- semanas do programa (J.3) -----------------------------------------

    private val inicio = LocalDate.parse("2026-08-05")   // uma QUARTA, de proposito

    @Test
    fun `semana 1 do programa comeca no started_at, nao na segunda-feira`() {
        // O programa comecou numa quarta. Os 7 dias a partir dela sao a semana 1 INTEIRA —
        // se a regua fosse a semana ISO, a semana 1 acabaria no domingo, com 5 dias.
        assertEquals(1, ProgressPolicy.semanaDoPrograma(LocalDate.parse("2026-08-05"), inicio))
        assertEquals(1, ProgressPolicy.semanaDoPrograma(LocalDate.parse("2026-08-11"), inicio))
        assertEquals(2, ProgressPolicy.semanaDoPrograma(LocalDate.parse("2026-08-12"), inicio))
    }

    @Test
    fun `sessao ANTERIOR ao programa cai em semana zero ou negativa, nao na semana 1`() {
        // Sessao mais velha que o programa existe: o programa foi criado depois. Truncar em
        // direcao a zero (o `/` do Kotlin) colocaria esse treino dentro da semana 1.
        assertEquals(0, ProgressPolicy.semanaDoPrograma(LocalDate.parse("2026-08-04"), inicio))
        assertEquals(0, ProgressPolicy.semanaDoPrograma(LocalDate.parse("2026-07-30"), inicio))
        assertEquals(-1, ProgressPolicy.semanaDoPrograma(LocalDate.parse("2026-07-28"), inicio))
    }

    @Test
    fun `semana 10 e a decima, contada de sete em sete`() {
        // 9 semanas depois do inicio = 63 dias
        assertEquals(10, ProgressPolicy.semanaDoPrograma(LocalDate.parse("2026-10-07"), inicio))
    }

    @Test
    fun `a faixa devolve uma posicao por semana, inclusive as vazias`() {
        val s = listOf(
            serie("2026-08-05", 60.0),   // semana 1 — fora da faixa pedida
            serie("2026-09-02", 50.0),   // semana 5
        )
        val faixa = ProgressPolicy.porSemanaDoPrograma(s, inicio, de = 4, ate = 7)

        assertEquals(listOf(4, 5, 6, 7), faixa.map { it.semana })
        assertEquals(listOf(0.0, 500.0, 0.0, 0.0), faixa.map { it.kg })
    }

    @Test
    fun `faixa invertida e recusada em vez de devolver lista vazia`() {
        // Lista vazia esconderia o erro de quem chamou; a tela mostraria "sem dado" e ninguem
        // saberia que a faixa estava ao contrario.
        assertFailsWith<IllegalArgumentException> {
            ProgressPolicy.porSemanaDoPrograma(emptyList(), inicio, de = 7, ate = 4)
        }
    }

    @Test
    fun `naFaixaDoPrograma recorta UMA vez para todos os blocos usarem o mesmo corte`() {
        val s = listOf(
            serie("2026-08-05", 60.0),   // semana 1
            serie("2026-09-02", 50.0),   // semana 5
            serie("2026-09-30", 40.0),   // semana 9
        )
        val recortado = ProgressPolicy.naFaixaDoPrograma(s, inicio, de = 4, ate = 7)

        assertEquals(1, recortado.size)
        assertEquals(500.0, ProgressPolicy.tonelagem(recortado))
    }

    // ---- janela de calendario (J.4.1) --------------------------------------

    /** 2026-10-01 e uma quinta; a segunda dessa semana e 28/09. */
    private val quinta = LocalDate.parse("2026-10-01")

    @Test
    fun `a janela comeca na segunda da primeira semana, nao no dia de hoje`() {
        // Com 8 semanas: 28/09 menos 7 semanas = 10/08. Cortar em "hoje menos 56 dias" daria
        // 06/08 e colocaria meia semana a mais no grafico.
        assertEquals(LocalDate.parse("2026-08-10"), ProgressPolicy.primeiraSemanaDaJanela(quinta, 8))
        assertEquals(LocalDate.parse("2026-04-06"), ProgressPolicy.primeiraSemanaDaJanela(quinta, 26))
    }

    @Test
    fun `janela de uma semana e a semana corrente inteira`() {
        assertEquals(LocalDate.parse("2026-09-28"), ProgressPolicy.primeiraSemanaDaJanela(quinta, 1))
    }

    /**
     * ⭐ O corte que faltava: sem ele cada bloco recortava sozinho e os tres discordavam — barras
     * em 8 semanas, linha de 1RM no historico inteiro, e a media de series dividindo o historico
     * inteiro por 8.
     */
    @Test
    fun `naJanelaDeCalendario deixa de fora o que e anterior a janela`() {
        val s = listOf(
            serie("2026-06-15", 60.0),   // fora da janela de 8, dentro da de 26
            serie("2026-09-28", 65.0),
        )

        assertEquals(1, ProgressPolicy.naJanelaDeCalendario(s, quinta, 8).size)
        assertEquals(2, ProgressPolicy.naJanelaDeCalendario(s, quinta, 26).size)
    }

    @Test
    fun `janela zero ou negativa e erro de programacao, nao janela vazia`() {
        // Devolver lista vazia esconderia a chamada errada e apareceria como "nao treinou".
        assertFailsWith<IllegalArgumentException> { ProgressPolicy.primeiraSemanaDaJanela(quinta, 0) }
        assertFailsWith<IllegalArgumentException> { ProgressPolicy.naJanelaDeCalendario(emptyList(), quinta, -1) }
    }

    @Test
    fun `evolucao no programa rotula o ponto pela semana, e pula a que nao teve o exercicio`() {
        val s = listOf(
            serie("2026-08-05", 60.0, reps = 8),    // semana 1
            serie("2026-09-02", 65.0, reps = 8),    // semana 5 — pulou da 2 a 4
        )
        val pontos = ProgressPolicy.evolucaoNoPrograma(s, agachamento, inicio)

        assertEquals(listOf(1, 5), pontos.map { it.semana })
        assertTrue(pontos[0].e1rm < pontos[1].e1rm)
    }

}
