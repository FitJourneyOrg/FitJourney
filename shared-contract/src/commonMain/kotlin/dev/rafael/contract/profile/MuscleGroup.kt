package dev.rafael.contract.profile

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * O vocabulário do USUÁRIO: o que ele filtra na biblioteca, escolhe como foco e vê em
 * `primary_muscles`/`secondary_muscles` do exercício (V57). **Não é o alvo de volume do motor** —
 * esse é `TargetMuscle`, no servidor, e os dois têm papéis diferentes.
 *
 * `CORE` está AQUI e NÃO em `TargetMuscle`: o abdômen é tag de estímulo do catálogo (46 exercícios,
 * a categoria `12_core` inteira) e filtro da biblioteca, mas não tem alvo semanal (V68). A Fatia 8
 * o tirou dos dois enums de uma vez e quebrou o filtro: o servidor descarta em silêncio o valor que
 * o enum não conhece. Devolvido em 2026-10-28. Ver [temAlvoDeVolume].
 */
@Serializable
enum class MuscleGroup {
    @SerialName("CHEST") CHEST,
    @SerialName("BACK") BACK,
    @SerialName("BICEPS") BICEPS,
    @SerialName("TRICEPS") TRICEPS,
    @SerialName("FOREARMS") FOREARMS,
    @SerialName("SHOULDERS") SHOULDERS,
    @SerialName("LEGS") LEGS,
    @SerialName("GLUTES") GLUTES,
    @SerialName("CORE") CORE,
    ;

    /**
     * `false` só para [CORE] — e **hoje, não por natureza**: o CORE não tem alvo de volume porque o
     * catálogo ainda não tem abdômen (alvo semanal 0, sem exercício elegível; débito "Catálogo sem
     * abdômen" em `docs/fontes/debitos.md`). **Quem puser core na lista principal tem de virar esta
     * chave** — `TargetMuscle.CORE`, a linha da tabela de volume e este `true` andam juntos.
     *
     * [REGRA] (`docs/regras/01`): **o que CONTA volume usa esta propriedade** (foco do quiz, volume
     * por grupo do progresso, `alvosDoFoco`); **o que CATALOGA usa o enum inteiro** (filtro da
     * biblioteca, `primary_muscles`). Não se mantém lista de exceções à mão em cada tela.
     */
    val temAlvoDeVolume: Boolean get() = this != CORE
}