package dev.rafael.contract.profile

import kotlinx.serialization.Serializable

/**
 * Modelo de divisão de treino (ARCH #29). O usuário escolhe no onboarding entre uma
 * shortlist CURADA e válida pro nº de dias (ver SplitCatalog); null = usa o recomendado.
 *
 * ⚠️ `label` e `description` NÃO são texto de UI, apesar de estarem em português. O KDoc dizia
 * que eram, e isso deixou de ser verdade sem ninguém reparar: o `StructureEngine` grava
 * `split.label` no `ProgramSkeleton` e o costura no `rationale`, e o `WeekSpread` COMPARA
 * (`split == SplitType.FULL_BODY.label`). São **chave interna do motor**, e traduzi-las quebraria
 * a geração de programa em silêncio.
 *
 * O texto que o usuário lê vem do catálogo do cliente: `SplitType.rotulo()` e `descricao()` em
 * `ui/Rotulos.kt` (ARCH #37). As frases coincidem hoje e vão divergir no inglês — e isso é o
 * comportamento correto, não uma divergência a consertar.
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
