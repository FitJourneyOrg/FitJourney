package dev.rafael.server.features.exercise.engine

import dev.rafael.contract.profile.TrainingEnvironment
import dev.rafael.contract.profile.TrainingEnvironment.*

/**
 * Mapeia cada valor de `equipment` (String, do catálogo curado) para o conjunto
 * de ambientes onde aquele equipamento é executável.
 *
 * REGRA DE NEGÓCIO em código, não no banco (ARCH #F.3):
 *  - equipment é String que cresce a cada curadoria; se fosse coluna de ambiente,
 *    cada equipamento novo exigiria migration. Em código, é uma linha.
 *  - "onde a garrafa d'água funciona" é julgamento nosso, não fato do catálogo.
 *
 * Camadas:
 *  - Só ACADEMIA: exige equipamento pesado/fixo (barra, máquina, polia, funcionais de academia).
 *  - ACADEMIA + HALTERES_CASA: halteres e acessórios de um cantinho em casa.
 *  - Universais: peso corporal e acessórios baratos — todos os ambientes.
 *  - Elásticos entram em ELASTICOS além de casa/academia.
 *
 * Equipamento não mapeado → cai no fallback (só ACADEMIA_COMPLETA), conservador:
 * um equipamento desconhecido não é assumido como disponível em casa.
 */
object EquipmentEnvironmentMap {

    private val ALL = setOf(ACADEMIA_COMPLETA, HALTERES_CASA, PESO_CORPORAL, ELASTICOS)

    private val map: Map<String, Set<TrainingEnvironment>> = mapOf(
        // --- só academia (equipamento pesado/fixo) ---
        "BARBELL" to setOf(ACADEMIA_COMPLETA),
        "MACHINE" to setOf(ACADEMIA_COMPLETA),
        "CABLE" to setOf(ACADEMIA_COMPLETA),
        "LANDMINE" to setOf(ACADEMIA_COMPLETA),
        "ROPE" to setOf(ACADEMIA_COMPLETA),
        "TIRE" to setOf(ACADEMIA_COMPLETA),

        // --- academia + casa (halteres e acessórios) ---
        "DUMBBELL" to setOf(ACADEMIA_COMPLETA, HALTERES_CASA),
        "KETTLEBELL" to setOf(ACADEMIA_COMPLETA, HALTERES_CASA),
        "WEIGHT_PLATE" to setOf(ACADEMIA_COMPLETA, HALTERES_CASA),
        "MEDICINE_BALL" to setOf(ACADEMIA_COMPLETA, HALTERES_CASA),
        "STABILITY_BALL" to setOf(ACADEMIA_COMPLETA, HALTERES_CASA),
        "BOSU" to setOf(ACADEMIA_COMPLETA, HALTERES_CASA),
        "SUSPENSION" to setOf(ACADEMIA_COMPLETA, HALTERES_CASA), // TRX

        // --- só casa (substituto improvisado) ---
        "IMPROVISED" to setOf(HALTERES_CASA), // garrafa d'água = halter caseiro

        // --- elásticos (+ casa + academia) ---
        "BAND" to setOf(ACADEMIA_COMPLETA, HALTERES_CASA, ELASTICOS),
        "GYMSTICK" to setOf(ACADEMIA_COMPLETA, HALTERES_CASA, ELASTICOS), // bastão + elástico

        // --- universais (peso corporal e acessórios baratos) ---
        "BODYWEIGHT" to ALL,
        "PUSHUP_BARS" to ALL,
        "STEP" to ALL,
    )

    /** Ambientes onde o equipamento é executável. Desconhecido → só academia (conservador). */
    fun environmentsFor(equipment: String): Set<TrainingEnvironment> =
        map[equipment] ?: setOf(ACADEMIA_COMPLETA)

    /** Os equipamentos que "contam" para um ambiente — usado no WHERE do pré-filtro. */
    fun equipmentsFor(environment: TrainingEnvironment): Set<String> =
        map.filterValues { environment in it }.keys
}