package dev.rafael.contract.profile

import kotlinx.serialization.Serializable

/**
 * Modelo de divisão de treino (ARCH #29). O usuário escolhe no onboarding entre uma
 * shortlist CURADA e válida pro nº de dias (ver SplitCatalog); null = usa o recomendado.
 * label/description são texto de UI (mostrados no quiz).
 */
@Serializable
enum class SplitType(val label: String, val description: String) {
    FULL_BODY("Full Body", "Corpo inteiro em todo treino."),
    UPPER_LOWER("Upper/Lower", "Superiores num dia, inferiores no outro."),
    UPPER_LOWER_FULL("Upper/Lower/Full", "Superior, inferior e um dia de corpo inteiro."),
    PUSH_PULL_LEGS("Push/Pull/Legs", "Empurrar, puxar e pernas."),
    UL_PPL("Upper/Lower + PPL", "Híbrido: abre com Upper/Lower e fecha com Push/Pull/Legs."),
    ARNOLD("Arnold", "Peito+Costas, Pernas, Ombros+Braços."),
}
