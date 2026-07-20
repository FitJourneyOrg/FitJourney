package dev.rafael.server.features.exercise.engine

import dev.rafael.contract.profile.MuscleGroup
import dev.rafael.server.features.exercise.models.MovementPattern

/** Estruturas do motor de estrutura (F.2). O esqueleto que a F.4 preenche. */

data class Slot(
    val kind: SlotKind,
    val pattern: MovementPattern? = null,   // slots compostos
    val muscle: MuscleGroup? = null,        // slots de isolamento/core
    val sets: Int,
    val repRange: String,
    val restSeconds: Int,
    val isFocus: Boolean = false,
)

enum class SlotKind { COMPOUND, ISOLATION, CORE }

data class DaySkeleton(val label: String, val slots: List<Slot>)

data class ProgramSkeleton(
    val days: List<DaySkeleton>,
    val split: String,
    val rationale: String,
)