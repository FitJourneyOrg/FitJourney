package dev.rafael.server.features.exercise.engine

import dev.rafael.contract.profile.BodyLimitation
import dev.rafael.contract.profile.Level
import dev.rafael.contract.profile.TrainingEnvironment
import dev.rafael.server.features.exercise.db.ExercisesTable
import dev.rafael.server.features.exercise.db.toExercise
import dev.rafael.server.features.exercise.models.Exercise
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNotNull
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

/**
 * Pré-filtro do motor (Fatia F.3, ARCH #20). Reduz o catálogo ao pool viável:
 * ambiente + nível em SQL; limitações em código (débito: mover p/ && ao escalar).
 *
 * A query roda em transaction { } própria — o motor que chama poolFor() é lógica
 * pura e não abre transação. (Refactor futuro (c): o service busca o pool e passa
 * pronto ao motor, tornando o motor 100% sem-banco.)
 */
class ExercisePreFilter {

    fun poolFor(
        environment: TrainingEnvironment,
        limitations: List<BodyLimitation>,
        level: Level,
    ): List<Exercise> {
        val allowedEquipment = EquipmentEnvironmentMap.equipmentsFor(environment)
        val allowedLevels = levelsUpTo(level)
        val blocked = limitations.map { it.name }

        return transaction {
            ExercisesTable
                .selectAll()
                .where {
                    (ExercisesTable.modality.isNotNull()) and
                            (ExercisesTable.modality eq "STRENGTH") and
                            (ExercisesTable.equipment inList allowedEquipment) and
                            (ExercisesTable.level inList allowedLevels)
                }
                .map { it.toExercise() }
                .filter { ex -> blocked.none { b -> ex.contraindications.any { it.name == b } } }
        }
    }

    private fun levelsUpTo(level: Level): List<String> = when (level) {
        Level.BEGINNER -> listOf("BEGINNER")
        Level.INTERMEDIATE -> listOf("BEGINNER", "INTERMEDIATE")
        Level.ADVANCED -> listOf("BEGINNER", "INTERMEDIATE", "ADVANCED")
    }
}