package dev.rafael.server.features.exercise.engine

import dev.rafael.contract.profile.Accessory
import dev.rafael.contract.profile.Level
import dev.rafael.contract.profile.TrainingEnvironment
import dev.rafael.server.features.exercise.db.ExercisesTable
import dev.rafael.server.features.exercise.db.toExercise
import dev.rafael.server.features.exercise.models.Exercise
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNotNull
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

/**
 * Pré-filtro do motor (Fatia F.3, ARCH #20). Reduz o catálogo ao pool viável: ambiente + nível,
 * em SQL.
 *
 * CONTRAINDICAÇÃO NÃO FILTRA MAIS (decisão do Rafael, 21/10, com a V65). Como filtro duro ela
 * esvaziava grupo inteiro: dor no ombro deixava 1 exercício de peito na lista principal, dor no
 * joelho deixava 0 de quadríceps e dor no punho deixava 1 de antebraço. O dado curado continua
 * em `exercises.contraindications` e serve à biblioteca; no onboarding, a limitação vira aviso
 * ao usuário, e `profiles.limitations` segue guardando a resposta.
 *
 * AMBIENTE e ACESSÓRIO (V65/V66) entram aqui. O `ambiente` curado só existe na lista principal,
 * então a regra é "ambiente nulo OU compatível": fora da lista o ambiente continua saindo do
 * equipamento, pelo EquipmentEnvironmentMap, como antes. Os `requires_*` valem para o catálogo
 * inteiro e são comparados em código, com o conjunto que o usuário declarou.
 *
 * Medido no simulador antes de existir: em casa, sem acessório declarado, 131 das vagas ficavam
 * sem exercício; com cadeira e banco, nenhuma.
 *
 * A query roda em transaction { } própria — o motor que chama poolFor() é lógica
 * pura e não abre transação. (Refactor futuro (c): o service busca o pool e passa
 * pronto ao motor, tornando o motor 100% sem-banco.)
 */
class ExercisePreFilter {

    fun poolFor(
        environment: TrainingEnvironment,
        level: Level,
        accessories: Set<Accessory> = Accessory.entries.toSet(),
    ): List<Exercise> {
        // null = academia (sem filtro de equipamento); set = casa (só halter/corporal/elástico)
        val homeEquipment = EquipmentEnvironmentMap.equipmentsFor(environment)
        val allowedLevels = levelsUpTo(level)
        val ambientes = when (environment) {
            TrainingEnvironment.ACADEMIA -> listOf("ACADEMIA", "AMBOS")
            TrainingEnvironment.CASA -> listOf("CASA", "AMBOS")
        }

        return transaction {
            ExercisesTable
                .selectAll()
                .where {
                    val cond = (ExercisesTable.modality.isNotNull()) and
                            (ExercisesTable.modality eq "STRENGTH") and
                            (ExercisesTable.level inList allowedLevels) and
                            (ExercisesTable.ambiente.isNull() or (ExercisesTable.ambiente inList ambientes))
                    if (homeEquipment != null) {
                        cond and (ExercisesTable.equipment inList homeEquipment.toList())
                    } else {
                        cond
                    }
                }
                .map { it.toExercise() }
                .filter { ex -> accessories.containsAll(ex.acessorios) }
        }
    }

    private fun levelsUpTo(level: Level): List<String> = when (level) {
        Level.BEGINNER -> listOf("BEGINNER")
        Level.INTERMEDIATE -> listOf("BEGINNER", "INTERMEDIATE")
        Level.ADVANCED -> listOf("BEGINNER", "INTERMEDIATE", "ADVANCED")
    }
}