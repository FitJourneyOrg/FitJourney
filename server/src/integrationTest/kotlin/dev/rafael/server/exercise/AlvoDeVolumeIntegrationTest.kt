package dev.rafael.server.exercise

import dev.rafael.server.BancoDeTeste
import dev.rafael.server.features.exercise.db.ExercisesTable
import dev.rafael.server.features.exercise.engine.TargetMuscle
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.exceptions.ExposedSQLException
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertThrows

/**
 * Trava a **V64** (alvo de volume do motor, fatia 0 do motor v3), contra Postgres REAL.
 *
 * ## O defeito
 *
 * O `SlotFiller` casava músculo com exercício por dois critérios que se sobrepõem — padrão de
 * movimento para QUADS/POSTERIOR, etiqueta em `primary_muscles` (array) para o resto. Cinco dos 79
 * da lista principal casavam em dois alvos, e quem ficava com eles dependia da ordem de
 * preenchimento. Nada que o motor medisse valia.
 *
 * A V64 dá a cada exercício **uma** coluna de **um** valor. Este arquivo prova que o valor chegou,
 * que o banco recusa vocabulário inventado, e que nenhum alvo do modelo de volume nasceu sem
 * candidato — o defeito que a primeira proposta de critério (GLUTES por `HIP_THRUST`, padrão que
 * não existe) teria criado sem nenhum erro aparecer.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AlvoDeVolumeIntegrationTest {

    @BeforeAll
    fun setup() {
        BancoDeTeste.dataSource
        BancoDeTeste.limpar()
    }

    private fun alvoDe(nome: String): String? = transaction {
        ExercisesTable.selectAll().first { it[ExercisesTable.name] == nome }[ExercisesTable.volumeTarget]
    }

    private fun contagemPorAlvo(): Map<String, Int> = transaction {
        ExercisesTable.selectAll()
            .mapNotNull { it[ExercisesTable.volumeTarget] }
            .groupingBy { it }.eachCount()
    }

    /**
     * O CHECK do banco e o enum do Kotlin descrevem o mesmo vocabulário. Se um dia `TargetMuscle`
     * ganhar um valor e a constraint não, o motor terá um alvo que nenhum exercício pode receber.
     */
    @Test
    fun `o vocabulario do CHECK e exatamente o de TargetMuscle`() {
        val definicao = BancoDeTeste.dataSource.connection.use { c ->
            c.createStatement().use { st ->
                st.executeQuery(
                    "SELECT pg_get_constraintdef(oid) FROM pg_constraint " +
                        "WHERE conname = 'chk_exercises_volume_target'",
                ).use { rs -> if (rs.next()) rs.getString(1) else null }
            }
        }
        val noBanco = Regex("'([A-Z_]+)'").findAll(definicao.orEmpty()).map { it.groupValues[1] }.toSet()

        assertEquals(TargetMuscle.entries.map { it.name }.toSet(), noBanco)
    }

    /** Caminho de falha (C2): `LEGS` é vocabulário do usuário, não do motor, e o banco recusa. */
    @Test
    fun `alvo fora do vocabulario e recusado pelo banco`() {
        assertThrows<ExposedSQLException> {
            transaction {
                ExercisesTable.update({ ExercisesTable.name eq "Leg Press 45°" }) {
                    it[volumeTarget] = "LEGS"
                }
            }
        }
        assertEquals("QUADS", alvoDe("Leg Press 45°"))
    }

    @Test
    fun `os que casavam em dois alvos agora tem um so`() {
        assertEquals("QUADS", alvoDe("Agachamento Livre com Barra"))
        assertEquals("QUADS", alvoDe("Afundo com Halteres"))
        assertEquals("QUADS", alvoDe("Afundo com Barra"))
        assertEquals("POSTERIOR", alvoDe("Levantamento Terra"))
        assertEquals("CHEST", alvoDe("Pullover com Halter"))
    }

    /**
      * A Cadeira Adutora era o NULL explicado no rodapé da V64: ficava fora da contabilidade por
      * não haver alvo de adutor. Na revisão de 21/10 ela entrou em POSTERIOR — não existe alvo de
      * adutor e o critério anatômico mais próximo é o posterior de coxa. Está na relevância 3,
      * então o motor só chega nela depois de esgotar o resto do grupo.
      */
    @Test
    fun `adutora entrou em posterior e o crucifixo inverso e deltoide`() {
        assertEquals("POSTERIOR", alvoDe("Cadeira Adutora"))
        assertEquals("SHOULDERS", alvoDe("Crucifixo Inverso Apoiado no Banco Inclinado"))
        assertEquals("GLUTES", alvoDe("Cadeira Abdutora"))
    }

    /**
      * A contagem mudou com a revisão da lista principal (21/10): 79 -> 147. A lista de 79 deixava
      * perfis inteiros sem exercício — quem treina em casa, o iniciante na academia e os grupos
      * pequenos. Os 68 que entraram já estavam curados no catálogo; nenhum exercício foi criado.
      * A V64 foi REGERADA (nunca tinha sido aplicada), não emendada por uma migration nova.
      *
      * Os três últimos entraram depois da primeira varredura do simulador, que mostrou vaga de
      * isolamento sem candidato em casa: **Agachamento Sissy** e **Agachamento Sissy Ajoelhado**
      * em QUADS (13 -> 15) e **Extensão de Ombro com Faixa Elástica** em BACK (20 -> 21).
      */
    @Test
    fun `a lista principal recebeu alvo e nada fora dela recebeu`() {
        assertEquals(
            mapOf(
                "CHEST" to 23, "BACK" to 21, "SHOULDERS" to 17, "BICEPS" to 13, "TRICEPS" to 13,
                "FOREARMS" to 8, "QUADS" to 15, "POSTERIOR" to 11, "CALVES" to 10, "GLUTES" to 16,
            ),
            contagemPorAlvo(),
        )
    }

    /**
     * Regra escrita contra valor que ninguém verificou se acontece: um alvo do modelo de volume
     * sem nenhum exercício faz o motor planejar séries que não consegue entregar. O CORE saiu do
     * motor na V68 (Fatia 8) e não é mais um `TargetMuscle`: agora NENHUM alvo pode ficar vazio.
     */
    @Test
    fun `todo alvo do modelo de volume tem candidato`() {
        val semCandidato = TargetMuscle.entries.map { it.name }.toSet() - contagemPorAlvo().keys

        assertTrue(semCandidato.isEmpty()) { "alvo sem nenhum exercício: $semCandidato" }
    }
}
