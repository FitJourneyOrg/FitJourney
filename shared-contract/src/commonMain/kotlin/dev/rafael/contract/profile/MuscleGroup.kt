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
     * `false` só para [CORE]: sem alvo de volume o foco nele não teria efeito algum, então o quiz
     * não o oferece. A biblioteca (filtro) oferece todos.
     */
    val temAlvoDeVolume: Boolean get() = this != CORE
}