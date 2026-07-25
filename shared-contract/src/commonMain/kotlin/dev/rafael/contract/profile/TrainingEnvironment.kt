package dev.rafael.contract.profile

import kotlinx.serialization.Serializable

/**
 * Ambiente/equipamento disponível ao usuário. Condiciona a geração por IA.
 * ARCH #28: produto suporta só dois contextos.
 *  - ACADEMIA: máquinas, barras, cabos, halteres — tudo.
 *  - CASA: halteres + peso corporal + elásticos.
 */
@Serializable
enum class TrainingEnvironment {
    ACADEMIA,
    CASA,
}