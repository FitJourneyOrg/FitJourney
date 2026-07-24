package dev.rafael.server.features.exercise.engine

import dev.rafael.contract.profile.Goal
import dev.rafael.contract.profile.Level
import dev.rafael.contract.profile.MuscleGroup
import kotlin.math.roundToInt

/**
 * Motor de estrutura (F.2, reescrito no ARCH #27). A variável-mãe é o VOLUME por
 * músculo (séries/semana), não slots por padrão. Fluxo:
 *   1. Alvo semanal por músculo (VolumeTable + foco).
 *   2. Split → em que dias cada músculo cai (define a frequência).
 *   3. Por (dia, músculo): séries da sessão = alvo/frequência → nº de exercícios
 *      (piso de 3 séries por exercício, ARCH #27 §3.3).
 *   4. Papel por posição → reps/descanso/RIR (§3.2).
 *
 * Determinístico, sem LLM (ARCH #20). Perna fina; idade fica pra fatia própria.
 */
class StructureEngine {

    private val FOCUS_BONUS = 3
    private val SESSION_MIN_SETS = 3        // piso de 3 séries por exercício
    private val MAX_SETS_PER_EXERCISE = 5   // teto de séries por exercício (densifica quando falta espaço)
    private val SESSION_MAX_SETS = 8        // teto por músculo/sessão antes de junk volume
    private val SESSION_MAX_EXERCISES = 6   // teto de exercícios por sessão (usabilidade)
    private val SETS_PER_EXERCISE = 3       // divisor da contagem "natural" de exercícios

    fun buildSkeleton(
        goal: Goal,
        level: Level,
        daysPerWeek: Int,
        focusMuscles: Set<MuscleGroup>,
    ): ProgramSkeleton {
        val days = daysPerWeek.coerceIn(2, 6)

        // 1. Alvo semanal por músculo (+ foco, respeitando o MRV).
        val focusTargets = focusMuscles.flatMap { VolumeTable.targetsForFocus(it) }.toSet()
        val weekly = TargetMuscle.entries.associateWith { m ->
            val base = VolumeTable.weeklySets(m, level, goal)
            val bonus = if (m in focusTargets) FOCUS_BONUS else 0
            minOf(base + bonus, VolumeTable.mrv(m))
        }

        // 2. Split → dias e seus músculos.
        val template = splitTemplate(days)

        // 3. Frequência = em quantos dias cada músculo aparece.
        val frequency = TargetMuscle.entries.associateWith { m ->
            template.count { (_, muscles) -> m in muscles }.coerceAtLeast(1)
        }

        // 4. Monta cada dia, respeitando o teto de exercícios por sessão.
        val daySkeletons = template.mapIndexed { dayIndex, pair ->
            val (label, muscles) = pair
            val trained = trainedMuscles(muscles, dayIndex)
            val sessionSets = trained.associateWith { m ->
                (weekly.getValue(m).toDouble() / frequency.getValue(m))
                    .roundToInt().coerceIn(SESSION_MIN_SETS, SESSION_MAX_SETS)
            }
            val exerciseCount = allocateExercises(trained, sessionSets)
            val slots = trained.flatMap { m ->
                val n = exerciseCount.getValue(m)
                // menos exercícios → mais séries cada (3..5), preservando o volume do músculo.
                val setsPer = (sessionSets.getValue(m).toDouble() / n)
                    .roundToInt().coerceIn(SESSION_MIN_SETS, MAX_SETS_PER_EXERCISE)
                (0 until n).map { i ->
                    val role = roleFor(m, i)
                    val p = RoleParams.paramsFor(role, level, goal)
                    Slot(m, role, setsPer, p.repRange, p.restSeconds, p.rir)
                }
            }.sortedBy { it.role.ordinal }   // compostos pesados primeiro no dia
            DaySkeleton(label, slots)
        }

        return ProgramSkeleton(daySkeletons, splitName(days), rationale(days, focusMuscles))
    }

    /**
     * Músculos treinados no dia respeitando o teto. Se cabem, todos entram. Se não
     * (full body = 9 músculos), mantém os compostos grandes e ROTACIONA os pequenos
     * entre os dias (cada dia cobre 1 pequeno diferente; o resto vem indireto dos compostos).
     */
    private fun trainedMuscles(muscles: Set<TargetMuscle>, dayIndex: Int): List<TargetMuscle> {
        if (muscles.size <= SESSION_MAX_EXERCISES) {
            return canonicalOrder.filter { it in muscles }
        }
        val bigs = bigMuscles.filter { it in muscles }
        val smalls = smallMuscles.filter { it in muscles }
        val budgetForSmall = (SESSION_MAX_EXERCISES - bigs.size).coerceAtLeast(0)
        val keptSmalls = rotate(smalls, dayIndex).take(budgetForSmall).toSet()
        val kept = bigs.toSet() + keptSmalls
        return canonicalOrder.filter { it in kept }
    }

    private fun <T> rotate(list: List<T>, by: Int): List<T> {
        if (list.isEmpty()) return list
        val n = ((by % list.size) + list.size) % list.size
        return list.drop(n) + list.take(n)
    }

    /** Distribui o orçamento de exercícios: 1 por músculo + resto pra quem tem mais volume (até o "natural"). */
    private fun allocateExercises(
        trained: List<TargetMuscle>,
        sessionSets: Map<TargetMuscle, Int>,
    ): Map<TargetMuscle, Int> {
        val natural = trained.associateWith { m ->
            (sessionSets.getValue(m).toDouble() / SETS_PER_EXERCISE).roundToInt().coerceAtLeast(1)
        }
        val alloc = trained.associateWith { 1 }.toMutableMap()
        var used = trained.size
        while (used < SESSION_MAX_EXERCISES) {
            val candidate = trained
                .filter { alloc.getValue(it) < natural.getValue(it) }
                .maxByOrNull { sessionSets.getValue(it).toDouble() / alloc.getValue(it) }
                ?: break
            alloc[candidate] = alloc.getValue(candidate) + 1
            used++
        }
        return alloc
    }

    /** Posição do exercício dentro do músculo → papel. */
    private fun roleFor(target: TargetMuscle, index: Int): SlotRole = when (target) {
        // Isolamento puro (não tem composto-âncora confiável no nosso modelo).
        TargetMuscle.CALVES, TargetMuscle.CORE, TargetMuscle.ARMS -> SlotRole.ISOLAMENTO
        else -> when (index) {
            0 -> SlotRole.COMPOSTO_PESADO
            1 -> SlotRole.COMPOSTO_ACESSORIO
            else -> SlotRole.ISOLAMENTO
        }
    }

    // ---- split: em que dias cada músculo cai (frequência-primeiro, ~2x) ----

    private fun splitTemplate(days: Int): List<Pair<String, Set<TargetMuscle>>> = when (days) {
        2 -> listOf("Full Body A" to FULL, "Full Body B" to FULL)
        3 -> listOf("Full Body A" to FULL, "Full Body B" to FULL, "Full Body C" to FULL)
        4 -> listOf("Upper A" to UPPER, "Lower A" to LOWER, "Upper B" to UPPER, "Lower B" to LOWER)
        5 -> listOf("Upper" to UPPER, "Lower" to LOWER, "Push" to PUSH, "Pull" to PULL, "Legs" to LEGS)
        else -> listOf(
            "Push A" to PUSH, "Pull A" to PULL, "Legs A" to LEGS,
            "Push B" to PUSH, "Pull B" to PULL, "Legs B" to LEGS,
        )
    }

    private fun splitName(days: Int) = when (days) {
        2, 3 -> "Full Body"; 4 -> "Upper/Lower"; 5 -> "Upper/Lower + PPL"; else -> "Push/Pull/Legs"
    }

    private fun rationale(days: Int, focus: Set<MuscleGroup>): String {
        val base = when (days) {
            2, 3 -> "Full Body em $days dias: cada músculo é estimulado com alta frequência, o que a ciência mostra ser mais eficaz em baixa frequência de treinos."
            4 -> "Upper/Lower em 4 dias: cada músculo 2x por semana, o ponto ideal para hipertrofia."
            5 -> "Upper/Lower + Push/Pull/Legs em 5 dias: mantém frequência 2x e distribui o volume por músculo."
            else -> "Push/Pull/Legs repetido em 6 dias: cada músculo 2x por semana, volume alto bem distribuído."
        }
        val volume = " O volume (séries/semana por músculo) é calibrado pelo seu nível — a base do modelo de hipertrofia."
        val f = if (focus.isEmpty()) "" else
            " Como você priorizou ${focus.joinToString(", ") { it.name }}, esses grupos recebem volume extra."
        return base + volume + f
    }

    // ---- grupos de músculos por tipo de dia ----
    private val UPPER = setOf(TargetMuscle.CHEST, TargetMuscle.BACK, TargetMuscle.SHOULDERS, TargetMuscle.ARMS)
    private val LOWER = setOf(TargetMuscle.QUADS, TargetMuscle.POSTERIOR, TargetMuscle.CALVES, TargetMuscle.GLUTES, TargetMuscle.CORE)
    private val PUSH = setOf(TargetMuscle.CHEST, TargetMuscle.SHOULDERS, TargetMuscle.ARMS)
    private val PULL = setOf(TargetMuscle.BACK, TargetMuscle.ARMS)
    private val LEGS = setOf(TargetMuscle.QUADS, TargetMuscle.POSTERIOR, TargetMuscle.CALVES, TargetMuscle.GLUTES)
    private val FULL = TargetMuscle.entries.toSet()

    /** Ordem canônica pra montagem determinística (grandes → pequenos). */
    private val canonicalOrder = listOf(
        TargetMuscle.CHEST, TargetMuscle.BACK, TargetMuscle.SHOULDERS,
        TargetMuscle.QUADS, TargetMuscle.POSTERIOR, TargetMuscle.GLUTES,
        TargetMuscle.ARMS, TargetMuscle.CALVES, TargetMuscle.CORE,
    )

    // Prioridade quando o dia estoura o teto: compostos grandes sempre; pequenos rotacionam.
    private val bigMuscles = listOf(
        TargetMuscle.CHEST, TargetMuscle.BACK, TargetMuscle.QUADS,
        TargetMuscle.POSTERIOR, TargetMuscle.SHOULDERS,
    )
    private val smallMuscles = listOf(
        TargetMuscle.GLUTES, TargetMuscle.ARMS, TargetMuscle.CALVES, TargetMuscle.CORE,
    )
}
