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
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.selectAll

/**
 * Pré-filtro do motor de geração (Fatia F.3, ARCH #20).
 *
 * Reduz o catálogo de 430 exercícios curados ao POOL que o usuário PODE fazer,
 * ANTES de o preenchedor (F.4) escolher. Três filtros combinados numa query:
 *
 *   1. AMBIENTE   — só exercícios cujo equipment é executável no ambiente do usuário
 *                   (via EquipmentEnvironmentMap, regra em código).
 *   2. LIMITAÇÕES — exclusão DURA: remove exercícios cuja contraindicação intersecta
 *                   as limitações do usuário. É o mecanismo de segurança do PAR-Q em
 *                   SQL (não em prompt) — array nativo && (ARCH #21).
 *   3. NÍVEL      — exercícios até o nível do usuário (avançado faz de iniciante; o
 *                   inverso não).
 *
 * [INV] Só entram no pool exercícios com taxonomia (modality IS NOT NULL): os 542
 * não-curados são invisíveis ao motor por design.
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

        return ExercisesTable
            .selectAll()
            .where {
                // (1) só exercícios com taxonomia — os 542 não-curados são invisíveis
                (ExercisesTable.modality.isNotNull()) and
                        // só força entra no motor (mobility/cardio/plyometric ficam fora)
                        (ExercisesTable.modality eq "STRENGTH") and
                        // (2) AMBIENTE: equipment executável aqui — reduz o grosso em SQL
                        (ExercisesTable.equipment inList allowedEquipment) and
                        // (3) NÍVEL: até o do usuário — em SQL
                        (ExercisesTable.level inList allowedLevels)
            }
            .map { it.toExercise() }
            // (4) LIMITAÇÕES: exclusão dura, filtrada em CÓDIGO no v1.
            //   Decisão (débito registrado): o ARCH #21 fez contraindications array nativo
            //   PARA permitir filtrar em SQL via && (overlap) com índice GIN — preparação p/
            //   escala. Mas aqui o pool já foi reduzido a ~200 por (2)+(3); filtrar limitações
            //   em memória sobre isso é imperceptível e mais simples. O array nativo + GIN
            //   continuam prontos: quando o catálogo crescer muito, mover este filtro p/ o
            //   WHERE via && cumpre o #21 sem retrabalho de schema.
            .filter { ex -> blocked.none { b -> ex.contraindications.any { it.name == b } } }
    }

    /** Níveis que um usuário do nível dado pode executar (inclusivo, cumulativo). */
    private fun levelsUpTo(level: Level): List<String> = when (level) {
        Level.BEGINNER -> listOf("BEGINNER")
        Level.INTERMEDIATE -> listOf("BEGINNER", "INTERMEDIATE")
        Level.ADVANCED -> listOf("BEGINNER", "INTERMEDIATE", "ADVANCED")
    }
}