package dev.rafael.contract.profile

import kotlinx.serialization.Serializable

/**
 * PAR-Q simplificado (gate de saúde, §3.2). Coletado no onboarding, revalidado no servidor
 * antes de qualquer geração por IA. A autoridade do gate é do backend; gateSatisfied é a
 * definição compartilhada que ambos os lados usam.
 */
@Serializable
data class HealthScreening(
    val hasCardiacCondition: Boolean = false,
    val hasChestPainDuringActivity: Boolean = false,
    val hasJointOrBoneIssue: Boolean = false,
    val takesContinuousMedication: Boolean = false,
    val acknowledgedRisk: Boolean = false,
) {
    val hasAnyRisk: Boolean
        get() = hasCardiacCondition || hasChestPainDuringActivity ||
                hasJointOrBoneIssue || takesContinuousMedication

    /** Gate satisfeito: sem risco declarado, OU risco reconhecido pelo usuário. */
    val gateSatisfied: Boolean
        get() = !hasAnyRisk || acknowledgedRisk
}
