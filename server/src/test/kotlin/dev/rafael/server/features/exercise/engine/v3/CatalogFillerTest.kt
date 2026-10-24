package dev.rafael.server.features.exercise.engine.v3

import dev.rafael.server.features.exercise.engine.TargetMuscle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Motor v3, Fatia 6 (`docs/trabalho/motor-v3-fatia-6.md`) — prova `CatalogFiller` contra
 * `tools/motor_sim/motor_v3.py::preencher()` (mesmos catálogos pequenos, saída verificada lá),
 * mais as duas regras que são só do Kotlin: desempate por nome e ordem por tipo entregue.
 */
class CatalogFillerTest {

    @Test
    fun `relevancia 1 preferida na primeira vaga do musculo, mesmo com prioridade pior`() {
        val catalogo = listOf(
            exercicioV3("Supino reto barra", TargetMuscle.CHEST, composto = true, equipamento = "BARBELL", familia = "supino", prioridade = 2, relevancia = 1),
            exercicioV3("Supino inclinado halteres", TargetMuscle.CHEST, composto = true, equipamento = "DUMBBELL", familia = "supino_inclinado", prioridade = 1, relevancia = 2),
            exercicioV3("Crucifixo halteres", TargetMuscle.CHEST, composto = false, equipamento = "DUMBBELL", familia = "crucifixo", prioridade = 1, relevancia = 1),
        )
        val vagas = listOf(
            SessionComposition.Vaga(TargetMuscle.CHEST, SessionComposition.Papel.COMPOSTO, 4),
            SessionComposition.Vaga(TargetMuscle.CHEST, SessionComposition.Papel.ISOLAMENTO, 2),
        )
        val disparos = mutableMapOf<String, Int>()
        val vazias = mutableListOf<Triple<String, TargetMuscle, SessionComposition.Papel>>()
        val resultado = CatalogFiller.preencher(listOf("Dia" to vagas), catalogo, disparos, vazias)

        val preenchidas = resultado.single().second
        assertEquals("Supino reto barra", preenchidas[0].exercicio?.name)
        assertEquals("Crucifixo halteres", preenchidas[1].exercicio?.name)
        assertEquals(emptyMap(), disparos)
        assertTrue(vazias.isEmpty())
    }

    @Test
    fun `familia repetida entre musculos diferentes no mesmo dia cai pro segundo candidato`() {
        val catalogo = listOf(
            exercicioV3("Remada curvada barra", TargetMuscle.BACK, composto = true, equipamento = "BARBELL", familia = "remada", prioridade = 1, relevancia = 1),
            exercicioV3("Desenvolvimento militar barra", TargetMuscle.SHOULDERS, composto = true, equipamento = "BARBELL", familia = "remada", prioridade = 1, relevancia = 1),
            exercicioV3("Desenvolvimento maquina", TargetMuscle.SHOULDERS, composto = true, equipamento = "MACHINE", familia = "desenvolvimento_maq", prioridade = 2, relevancia = 2),
        )
        val vagas = listOf(
            SessionComposition.Vaga(TargetMuscle.BACK, SessionComposition.Papel.COMPOSTO, 4),
            SessionComposition.Vaga(TargetMuscle.SHOULDERS, SessionComposition.Papel.COMPOSTO, 3),
        )
        val disparos = mutableMapOf<String, Int>()
        val resultado = CatalogFiller.preencher(listOf("Dia" to vagas), catalogo, disparos)

        val preenchidas = resultado.single().second
        assertEquals("Remada curvada barra", preenchidas[0].exercicio?.name)
        assertEquals("Desenvolvimento maquina", preenchidas[1].exercicio?.name)
        // nao e familia_repetida_na_sessao: havia alternativa (so nao era relevancia 1)
        assertEquals(mapOf("musculo_sem_relevancia_1" to 1), disparos)
    }

    @Test
    fun `papel_trocado dispara nos dois sentidos quando so existe o tipo contrario`() {
        val catalogo = listOf(
            exercicioV3("Triceps testa barra", TargetMuscle.TRICEPS, composto = true, equipamento = "BARBELL", familia = "triceps_testa"),
            exercicioV3("Elevacao lateral halteres", TargetMuscle.SHOULDERS, composto = false, equipamento = "DUMBBELL", familia = "elevacao_lateral"),
        )
        val vagas = listOf(
            SessionComposition.Vaga(TargetMuscle.TRICEPS, SessionComposition.Papel.ISOLAMENTO, 3),
            SessionComposition.Vaga(TargetMuscle.SHOULDERS, SessionComposition.Papel.COMPOSTO, 3),
        )
        val disparos = mutableMapOf<String, Int>()
        val resultado = CatalogFiller.preencher(listOf("Dia" to vagas), catalogo, disparos)

        val preenchidas = resultado.single().second
        val triceps = preenchidas.first { it.vaga.alvo == TargetMuscle.TRICEPS }
        val shoulders = preenchidas.first { it.vaga.alvo == TargetMuscle.SHOULDERS }
        assertEquals("Triceps testa barra", triceps.exercicio?.name)
        assertEquals(true, triceps.exercicio?.isCompound)
        assertEquals("Elevacao lateral halteres", shoulders.exercicio?.name)
        assertEquals(false, shoulders.exercicio?.isCompound)
        assertEquals(mapOf("papel_trocado" to 2), disparos)
    }

    @Test
    fun `vaga vazia so quando nao ha candidato nenhum, nem invertido`() {
        val catalogo = listOf(exercicioV3("Supino reto barra", TargetMuscle.CHEST, composto = true))
        val vagas = listOf(SessionComposition.Vaga(TargetMuscle.BACK, SessionComposition.Papel.COMPOSTO, 4))
        val vazias = mutableListOf<Triple<String, TargetMuscle, SessionComposition.Papel>>()
        val resultado = CatalogFiller.preencher(listOf("Dia" to vagas), catalogo, vaziasOut = vazias)

        assertNull(resultado.single().second.single().exercicio)
        assertEquals(listOf(Triple("Dia", TargetMuscle.BACK, SessionComposition.Papel.COMPOSTO)), vazias)
    }

    @Test
    fun `ordem final segue o tipo entregue, nao o papel original`() {
        // ISOLAMENTO de triceps vira composto (so existe barra); deve abrir a sessao, antes do
        // isolamento "de verdade" do ombro, mesmo entrando depois na lista de vagas original.
        val catalogo = listOf(
            exercicioV3("Triceps testa barra", TargetMuscle.TRICEPS, composto = true, familia = "triceps_testa"),
            exercicioV3("Elevacao lateral halteres", TargetMuscle.SHOULDERS, composto = false, familia = "elevacao_lateral"),
        )
        val vagas = listOf(
            SessionComposition.Vaga(TargetMuscle.SHOULDERS, SessionComposition.Papel.ISOLAMENTO, 3),
            SessionComposition.Vaga(TargetMuscle.TRICEPS, SessionComposition.Papel.ISOLAMENTO, 3),
        )
        val resultado = CatalogFiller.preencher(listOf("Dia" to vagas), catalogo)

        val ordem = resultado.single().second.map { it.vaga.alvo }
        assertEquals(listOf(TargetMuscle.TRICEPS, TargetMuscle.SHOULDERS), ordem)
    }

    @Test
    fun `desempate por nome mantem saida identica com o catalogo embaralhado`() {
        val catalogo = listOf(
            exercicioV3("Zelda push", TargetMuscle.CHEST, composto = true, familia = "f1", prioridade = 1),
            exercicioV3("Alfa push", TargetMuscle.CHEST, composto = true, familia = "f2", prioridade = 1),
            exercicioV3("Beta push", TargetMuscle.CHEST, composto = true, familia = "f3", prioridade = 1),
        )
        val vagas = listOf(SessionComposition.Vaga(TargetMuscle.CHEST, SessionComposition.Papel.COMPOSTO, 4))

        val saida1 = CatalogFiller.preencher(listOf("Dia" to vagas), catalogo).single().second.single().exercicio?.name
        val saida2 = CatalogFiller.preencher(listOf("Dia" to vagas), catalogo.reversed()).single().second.single().exercicio?.name
        val saida3 = CatalogFiller.preencher(listOf("Dia" to vagas), catalogo.shuffled(kotlin.random.Random(42))).single().second.single().exercicio?.name

        assertEquals("Alfa push", saida1)
        assertEquals(saida1, saida2)
        assertEquals(saida1, saida3)
    }
}
