package dev.rafael.server.features.exercise.engine

import dev.rafael.contract.profile.SplitType

/** Estruturas do motor de estrutura (F.2, reescrito no ARCH #26). O esqueleto que a F.4 preenche.
 *  Cada Slot agora carrega o músculo-alvo, o papel (→ reps/descanso/RIR) e o RIR resolvido. */

data class Slot(
    val target: TargetMuscle,
    val role: SlotRole,
    val sets: Int,
    val repRange: String,
    val restSeconds: Int,
    val rir: Int,
)

data class DaySkeleton(val label: String, val slots: List<Slot>)

/**
 * `split` é o enum (não mais `.label` colapsado aqui) — quem monta a frase para o usuário é o
 * cliente, via `SplitType.rotulo()`/`.descricao()` (`Rotulos.kt`, ARCH #37). O motor não escreve
 * mais prosa: `rationale` saiu daqui, do `Program` e do `ProgramDto`. Ver débito "rationale" no
 * handoff — o `StructureEngine` continua sabendo POR QUE montou o programa assim, mas quem diz
 * isso ao usuário, e em que idioma, é o cliente.
 */
data class ProgramSkeleton(
    val days: List<DaySkeleton>,
    val split: SplitType,
)
