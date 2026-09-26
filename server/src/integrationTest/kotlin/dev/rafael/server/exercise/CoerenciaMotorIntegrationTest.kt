package dev.rafael.server.exercise

import dev.rafael.server.BancoDeTeste
import dev.rafael.server.features.exercise.db.ExercisesTable
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

/**
 * Trava a checagem de coerência grupo↔padrão que o motor hoje NÃO faz sozinho (débito P3
 * "motor confia 100% na taxonomia sem sanity-check grupo↔padrão"), contra Postgres REAL.
 *
 * ## O risco concreto
 *
 * `SlotFiller.matchesTarget()` decide o alvo QUADS só por `movement_pattern` (`SQUAT`/`LUNGE`/
 * `KNEE_EXTENSION`), sem olhar `primary_muscles` -- diferente do alvo POSTERIOR (`HINGE`/
 * `KNEE_FLEXION`), que EXIGE `LEGS` no primário como segunda trava. Se um exercício tiver um
 * desses 3 padrões por erro de tagueamento (a mesma classe de defeito que a V57 achou 32 vezes:
 * silencioso, sem log, sem teste vermelho), o motor prescreve ele num treino de quadríceps sem
 * questionar. Este arquivo fecha esse buraco especificamente -- não redesenha a taxonomia
 * inteira (isso ficou pro SlotFiller v2, fora de escopo aqui por decisão do Rafael).
 *
 * ## Por que só QUADS, e não os outros 9 alvos
 *
 * Os outros alvos (`CHEST/BACK/SHOULDERS/BICEPS/TRICEPS/FOREARMS/CALVES/GLUTES/CORE`) decidem
 * por `category` ou `primary_muscles` -- não por `movement_pattern` -- e POSTERIOR já se protege
 * sozinho com o AND de `LEGS` dentro do próprio `matchesTarget`. QUADS é o único alvo com uma
 * trava faltando.
 *
 * ## Contra o banco, e não com fake
 *
 * Mesma razão do `CuradoriaTagueamentoIntegrationTest`: isto verifica dado de referência real
 * (migrations), não lógica Kotlin -- um fake provaria que a comparação de listas funciona, não
 * que o catálogo de verdade está consistente.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CoerenciaMotorIntegrationTest {

    @BeforeAll
    fun setup() {
        BancoDeTeste.dataSource
        BancoDeTeste.limpar()
    }

    private val padroesDeQuadriceps = setOf("SQUAT", "LUNGE", "KNEE_EXTENSION")

    /**
     * ⭐ O CAMINHO DE FALHA principal (C2). Se isto quebrar: algum exercício tem um padrão de
     * quadríceps mas não tem LEGS no primário -- o SlotFiller vai prescrever ele num slot de
     * QUADS mesmo assim, porque `matchesTarget` não confere isso hoje.
     */
    @Test
    fun `todo exercicio com padrao de quadriceps tem LEGS no primario`() {
        val suspeitos = transaction {
            ExercisesTable.selectAll()
                .filter { it[ExercisesTable.movementPattern] in padroesDeQuadriceps }
                .filterNot { "LEGS" in it[ExercisesTable.primaryMuscles].orEmpty() }
                .map { it[ExercisesTable.name] to it[ExercisesTable.movementPattern] }
        }

        assertTrue(suspeitos.isEmpty()) {
            "exercício com padrão de quadríceps mas sem LEGS no primário -- o motor confia " +
                "cegamente e vai prescrever errado:\n" +
                suspeitos.joinToString("\n") { (nome, padrao) -> "  $nome -> movement_pattern=$padrao" }
        }
    }
}
