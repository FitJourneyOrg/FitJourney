package dev.rafael.contract.profile

import kotlinx.serialization.Serializable

/** Ambiente/equipamento disponível ao usuário. Condiciona a geração por IA (Fatia D). */
@Serializable
enum class TrainingEnvironment {
    ACADEMIA_COMPLETA,   // acesso a máquinas, barras, halteres
    HALTERES_CASA,       // halteres (e banco) em casa
    PESO_CORPORAL,       // sem equipamento, só o corpo
    ELASTICOS,           // faixas de resistência
}