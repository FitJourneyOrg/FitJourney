package dev.rafael.server.features.exercise.engine

import dev.rafael.contract.profile.Goal
import dev.rafael.contract.profile.Level
import dev.rafael.contract.profile.MuscleGroup

/**
 * Modelo de volume por músculo (ARCH #27, Fatia A). Substitui o raciocínio antigo
 * de "slots por padrão por dia" pela variável-mãe da hipertrofia: séries semanais
 * efetivas por músculo (RP/Israetel). Determinístico, sem LLM (ARCH #20).
 *
 * Perna FINA: LEGS vira QUADS/POSTERIOR/CALVES (derivados de movement_pattern, dado
 * confiável). ARMS fica combinado (bíceps/tríceps não se separam por padrão — Fatia B).
 */
enum class TargetMuscle { CHEST, BACK, SHOULDERS, ARMS, QUADS, POSTERIOR, CALVES, GLUTES, CORE }

/** Papel do exercício → define reps/descanso/RIR (ARCH #27 §3.2). */
enum class SlotRole { COMPOSTO_PESADO, COMPOSTO_ACESSORIO, ISOLAMENTO }

/** Tabela de volume ratificada (ARCH #27 §3.1). Séries efetivas/semana. */
object VolumeTable {
    private data class V(val ini: Int, val inter: Int, val adv: Int, val mrv: Int)

    private val table = mapOf(
        TargetMuscle.CHEST to V(8, 14, 18, 22),
        TargetMuscle.BACK to V(10, 16, 20, 25),
        TargetMuscle.SHOULDERS to V(6, 12, 16, 22),
        TargetMuscle.ARMS to V(6, 10, 14, 20),
        TargetMuscle.QUADS to V(6, 12, 16, 20),
        TargetMuscle.POSTERIOR to V(6, 10, 14, 18),
        TargetMuscle.CALVES to V(5, 9, 12, 16),
        TargetMuscle.GLUTES to V(4, 8, 12, 16),
        TargetMuscle.CORE to V(5, 8, 10, 16),
    )

    /** Alvo semanal por músculo/nível. GENERAL_HEALTH usa o piso (MEV). */
    fun weeklySets(muscle: TargetMuscle, level: Level, goal: Goal): Int {
        val v = table.getValue(muscle)
        if (goal == Goal.GENERAL_HEALTH) return v.ini
        return when (level) {
            Level.BEGINNER -> v.ini
            Level.INTERMEDIATE -> v.inter
            Level.ADVANCED -> v.adv
        }
    }

    fun mrv(muscle: TargetMuscle): Int = table.getValue(muscle).mrv

    /** Foco (MuscleGroup grosso do quiz) → músculos-alvo finos que ganham bônus. */
    fun targetsForFocus(mg: MuscleGroup): Set<TargetMuscle> = when (mg) {
        MuscleGroup.CHEST -> setOf(TargetMuscle.CHEST)
        MuscleGroup.BACK -> setOf(TargetMuscle.BACK)
        MuscleGroup.SHOULDERS -> setOf(TargetMuscle.SHOULDERS)
        MuscleGroup.ARMS -> setOf(TargetMuscle.ARMS)
        MuscleGroup.LEGS -> setOf(TargetMuscle.QUADS, TargetMuscle.POSTERIOR, TargetMuscle.CALVES)
        MuscleGroup.GLUTES -> setOf(TargetMuscle.GLUTES)
        MuscleGroup.CORE -> setOf(TargetMuscle.CORE)
    }
}

/** Reps/descanso/RIR por papel (ARCH #27 §3.2), com modificador conservador. */
object RoleParams {
    data class P(val repRange: String, val restSeconds: Int, val rir: Int)

    /** Iniciante e GENERAL_HEALTH: RIR-piso 3 (cue "deixe no tanque", nunca falha). */
    fun paramsFor(role: SlotRole, level: Level, goal: Goal): P {
        val conservative = goal == Goal.GENERAL_HEALTH || level == Level.BEGINNER
        return when (role) {
            SlotRole.COMPOSTO_PESADO ->
                if (conservative) P("6-10", 150, 3) else P("5-8", 150, 2)
            SlotRole.COMPOSTO_ACESSORIO ->
                if (conservative) P("8-12", 105, 3) else P("8-12", 105, 1)
            SlotRole.ISOLAMENTO ->
                if (conservative) P("10-15", 75, 3) else P("12-20", 75, 1)
        }
    }
}
