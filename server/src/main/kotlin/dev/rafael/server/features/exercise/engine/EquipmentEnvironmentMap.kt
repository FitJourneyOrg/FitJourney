package dev.rafael.server.features.exercise.engine

import dev.rafael.contract.profile.TrainingEnvironment

/**
 * Mapeia `equipment` (String, do catálogo) → ambientes onde é executável (ARCH #28).
 *
 * Só dois ambientes agora:
 *  - ACADEMIA: vê TODO equipamento (academia tem tudo).
 *  - CASA: só halteres + peso corporal + elásticos.
 *
 * Regra em código, não no banco: "onde esse equipamento roda" é julgamento nosso e
 * muda sem migration. Equipamento desconhecido → só academia (conservador).
 */
object EquipmentEnvironmentMap {

    /** Equipamentos executáveis em casa: halter + corporal + elástico. */
    val HOME_EQUIPMENT: Set<String> = setOf("DUMBBELL", "BODYWEIGHT", "BAND")

    /** Ambientes onde o equipamento roda. Academia sempre; casa só o subconjunto caseiro. */
    fun environmentsFor(equipment: String): Set<TrainingEnvironment> =
        if (equipment in HOME_EQUIPMENT) {
            setOf(TrainingEnvironment.ACADEMIA, TrainingEnvironment.CASA)
        } else {
            setOf(TrainingEnvironment.ACADEMIA)
        }

    /**
     * Equipamentos que "contam" para o ambiente. CASA = subconjunto caseiro;
     * ACADEMIA = null (sem filtro de equipamento — academia vê tudo que é STRENGTH visível).
     */
    fun equipmentsFor(environment: TrainingEnvironment): Set<String>? =
        when (environment) {
            TrainingEnvironment.CASA -> HOME_EQUIPMENT
            TrainingEnvironment.ACADEMIA -> null
        }
}
