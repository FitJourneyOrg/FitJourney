package dev.rafael.server.features.exercise.engine

/** Estruturas do motor de estrutura (F.2, reescrito no ARCH #27). O esqueleto que a F.4 preenche.
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

data class ProgramSkeleton(
    val days: List<DaySkeleton>,
    val split: String,
    val rationale: String,
)
