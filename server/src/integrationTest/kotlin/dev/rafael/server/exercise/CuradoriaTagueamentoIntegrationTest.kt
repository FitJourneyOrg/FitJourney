package dev.rafael.server.exercise

import dev.rafael.contract.profile.MuscleGroup
import dev.rafael.server.BancoDeTeste
import dev.rafael.server.features.exercise.db.ExercisesTable
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

/**
 * Trava os vereditos da **V57** (curadoria de tagueamento), contra Postgres REAL.
 *
 * ## Por que este teste existe
 *
 * O defeito que a V57 corrigiu não quebrava nada: 32 exercícios chegavam ao cliente com
 * `primaryMuscles` VAZIO porque o repositório converte `TEXT[]` com
 * `mapNotNull { runCatching { MuscleGroup.valueOf(it) }.getOrNull() }` — **descarta valor
 * desconhecido em silêncio**. `TRAPEZIUS` estava em `primary_muscles` de 9 exercícios e não
 * existe em `MuscleGroup` (é `ExerciseCategory`, outra taxonomia). Resultado: sumiam do filtro
 * por músculo da Biblioteca e não contavam volume no motor, sem erro, sem log, sem teste
 * vermelho.
 *
 * > **Defeito que não grita precisa de alguém que pergunte.** Este arquivo é quem pergunta.
 *
 * ## Contra o banco, e não com fake
 *
 * O que se verifica aqui é o RESULTADO DE UMA MIGRATION sobre dado de referência. Um fake
 * provaria que o Kotlin sabe comparar listas — não que a V57 rodou, nem que o import seguinte
 * não desfez o que ela fez. O catálogo vem das migrations (ver `BancoDeTeste.limpar`, que
 * deliberadamente NÃO o trunca), então é o dado real que está sendo lido.
 *
 * ## O que este teste NÃO faz
 *
 * Não valida a coerência grupo↔padrão do catálogo inteiro — essa é a proposta que ficou para o
 * SlotFiller v2 (débito P3 "motor confia 100% na taxonomia sem sanity-check"), por decisão de
 * escopo. Aqui só se trava o que a V57 decidiu, exercício a exercício.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CuradoriaTagueamentoIntegrationTest {

    @BeforeAll
    fun setup() {
        BancoDeTeste.dataSource
        BancoDeTeste.limpar()
    }

    private fun musculosDe(nome: String): Pair<List<String>, List<String>>? = transaction {
        ExercisesTable.selectAll()
            .firstOrNull { it[ExercisesTable.name] == nome }
            ?.let {
                it[ExercisesTable.primaryMuscles].orEmpty().toList() to
                    it[ExercisesTable.secondaryMuscles].orEmpty().toList()
            }
    }

    /**
     * ⭐ O CAMINHO DE FALHA principal (C2), e a razão de tudo isto.
     *
     * Vale para o catálogo inteiro porque é barato e porque é exatamente o defeito que custou os
     * 32 invisíveis — não é o sanity-check grupo↔padrão que ficou para o v2, é só "o vocabulário
     * gravado existe no enum que o cliente desserializa".
     */
    @Test
    fun `nenhum exercicio tem musculo fora do enum MuscleGroup`() {
        val valido = MuscleGroup.entries.map { it.name }.toSet()

        val invalidos = transaction {
            ExercisesTable.selectAll().mapNotNull { linha ->
                val fora = (linha[ExercisesTable.primaryMuscles].orEmpty().toList() +
                    linha[ExercisesTable.secondaryMuscles].orEmpty().toList())
                    .filterNot { it in valido }
                if (fora.isEmpty()) null else linha[ExercisesTable.name] to fora
            }
        }

        assertTrue(invalidos.isEmpty()) {
            "Músculo fora de MuscleGroup — o servidor DESCARTA em silêncio e o exercício fica " +
                "invisível no filtro e no motor:\n" +
                invalidos.joinToString("\n") { (nome, fora) -> "  $nome -> $fora" }
        }
    }

    /** Os 9 que tinham `TRAPEZIUS` e por isso chegavam VAZIOS ao cliente. */
    @Test
    fun `familia do trapezio ficou em BACK, nao em TRAPEZIUS`() {
        listOf(
            "Encolhimento com Barra",
            "Encolhimento com Halteres",
            "Encolhimento na Máquina",
            "Encolhimento no Cabo",
            "Encolhimento acima da Cabeça",
            "Depressão Escapular no Banco",
            "Elevação em T Apoiado no Banco Inclinado",
            "Elevação em Y Apoiado no Banco Inclinado",
        ).forEach { nome ->
            val (prim, _) = musculosDe(nome) ?: error("exercício sumiu do catálogo: $nome")
            assertEquals(listOf("BACK"), prim, "$nome deveria ser BACK (convenção do catálogo)")
        }
    }

    /**
     * O "deltoide posterior espalhado em 3 categorias" do débito: crucifixo inverso e face pull
     * têm o MESMO alvo e estavam com primários diferentes conforme a categoria em que caíram.
     */
    @Test
    fun `deltoide posterior tem o mesmo primario em qualquer categoria`() {
        listOf(
            "Crucifixo Inverso Apoiado no Banco Inclinado",   // estava BACK (categoria TRAPEZIUS)
            "Crucifixo Inverso Curvado no Cabo",              // estava BACK (categoria TRAPEZIUS)
            "Crucifixo Inverso Curvado com Halteres",         // já estava SHOULDERS
            "Face Pull (Puxada para o Rosto)",                // estava TRAPEZIUS -> invisível
            "Face Pull Ajoelhado",                            // estava BACK
            "Face Pull com Cabos Cruzados",                   // estava BACK
        ).forEach { nome ->
            val (prim, _) = musculosDe(nome) ?: error("exercício sumiu do catálogo: $nome")
            assertEquals(listOf("SHOULDERS"), prim, "$nome: deltoide posterior é o alvo")
        }
    }

    /** Remada alta: deltoide primário, trapézio secundário — estava metade em BACK. */
    @Test
    fun `remada alta tem deltoide primario e trapezio secundario`() {
        listOf("Remada Alta com Barra", "Remada Alta no Cabo", "Remada Alta com Halteres")
            .forEach { nome ->
                val (prim, sec) = musculosDe(nome) ?: error("exercício sumiu do catálogo: $nome")
                assertEquals(listOf("SHOULDERS"), prim, "$nome: alvo é o deltoide")
                assertTrue("BACK" in sec) { "$nome deveria ter BACK como secundário, tem $sec" }
            }
    }

    /**
     * Os 23 órfãos da V55 — entraram no catálogo sem taxonomia nenhuma e eram invisíveis pelo
     * mesmo motivo dos 9 acima, só que por lista vazia em vez de valor inválido.
     */
    @Test
    fun `orfaos da V55 ganharam musculo primario`() {
        mapOf(
            "Supino Sentado na Máquina (Alavanca)" to "CHEST",
            "Supino Inclinado na Máquina (Alavanca)" to "CHEST",
            "Mesa Flexora Ajoelhado (Alavanca)" to "LEGS",
            "Coice de Glúteo na Máquina" to "GLUTES",
            "Mergulho entre Bancos" to "TRICEPS",
            "Hiperextensão Lombar no Banco Reto" to "BACK",
            "Encolhimento na Máquina (Alavanca)" to "BACK",
        ).forEach { (nome, esperado) ->
            val (prim, _) = musculosDe(nome) ?: error("exercício sumiu do catálogo: $nome")
            assertTrue(prim.isNotEmpty()) { "$nome continua sem primary_muscles — invisível" }
            assertEquals(esperado, prim.first(), "$nome")
        }
    }

    /**
     * A V57 mexeu em músculo e em NADA MAIS. A categoria é vocabulário de UI e ficou onde estava
     * de propósito (quem procura deltoide posterior filtra por `SHOULDERS` e acha os 11, em
     * qualquer categoria — o filtro por `MuscleGroup` da Biblioteca já resolve o "espalhado").
     */
    @Test
    fun `curadoria nao mexeu em categoria`() {
        val categoriaDe = { nome: String ->
            transaction {
                ExercisesTable.selectAll()
                    .firstOrNull { it[ExercisesTable.name] == nome }
                    ?.get(ExercisesTable.category)
            }
        }
        assertEquals("TRAPEZIUS", categoriaDe("Face Pull (Puxada para o Rosto)"))
        assertEquals("TRAPEZIUS", categoriaDe("Remada Alta com Barra"))
        assertEquals("SHOULDERS", categoriaDe("Crucifixo Inverso Curvado com Halteres"))
    }
}
