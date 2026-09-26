package dev.rafael.contract.profile

import kotlinx.serialization.Serializable

/**
 * Modelo de divisão de treino (ARCH #29). O usuário escolhe no onboarding entre uma
 * shortlist CURADA e válida pro nº de dias (ver SplitCatalog); null = usa o recomendado.
 *
 * `label` e `description` são só DOCUMENTAÇÃO do enum (jargão de academia, pra quem lê o
 * código saber do que se trata) — não são mais lidos em nenhum lugar do motor nem do cliente.
 * **Corrigido pela fatia "rationale derivado" (2026-09-22):** até então `label` era usado como
 * dado real — o `StructureEngine` gravava `split.label` no `ProgramSkeleton` e o costurava no
 * `rationale`, e o `WeekSpread` COMPARAVA por ele (`split == SplitType.FULL_BODY.label`). Esse
 * era exatamente o tipo de "campo que o servidor compara não é rótulo, mesmo escrito em
 * português" que a G.3 já tinha documentado noutro lugar. Agora o motor e o cliente comparam e
 * persistem a CHAVE do enum (`.name`) diretamente; nada mais lê `label`/`description` por código.
 *
 * O texto que o usuário lê vem do catálogo do cliente: `SplitType.rotulo()` e `descricao()` em
 * `ui/Rotulos.kt` (ARCH #37), que hoje traduzem a CHAVE (`split: String?` em `Program`/`ProgramDto`),
 * não este `label`/`description`.
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
