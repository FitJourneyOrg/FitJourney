package dev.rafael.server.features.exercise.engine

import dev.rafael.contract.profile.Goal
import dev.rafael.contract.profile.Level
import dev.rafael.contract.profile.MuscleGroup
import dev.rafael.contract.profile.SplitCatalog
import dev.rafael.contract.profile.SplitType
import kotlin.math.roundToInt

/**
 * Motor de estrutura (F.2, reescrito no ARCH #26). A variável-mãe é o VOLUME por
 * músculo (séries/semana), não slots por padrão. Fluxo:
 *   1. Alvo semanal por músculo (VolumeTable + foco).
 *   2. Split → em que dias cada músculo cai (define a frequência).
 *   3. Por (dia, músculo): séries da sessão = alvo/frequência → nº de exercícios
 *      (piso de 3 séries por exercício, ARCH #26 §3.3).
 *   4. Papel por posição → reps/descanso/RIR (§3.2).
 *
 * Determinístico, sem LLM (ARCH #20). Perna fina; idade fica pra fatia própria.
 */
class StructureEngine {

    private val FOCUS_BONUS = 3
    private val SESSION_MIN_SETS = 3          // piso de 3 séries por exercício
    private val MAX_SETS_PER_EXERCISE = 5     // teto de séries por exercício (densifica quando falta espaço)
    private val SESSION_MAX_SETS = 8          // teto por músculo/sessão antes de junk volume
    private val SESSION_MAX_EXERCISES = 6     // teto de exercícios por sessão (usabilidade)
    private val SESSION_MAX_SETS_TOTAL = 20   // teto de séries TOTAIS por sessão (evita junk volume)
    private val SETS_PER_EXERCISE = 3         // divisor da contagem "natural" de exercícios

    fun buildSkeleton(
        goal: Goal,
        level: Level,
        daysPerWeek: Int,
        focusMuscles: Set<MuscleGroup>,
        splitPreference: SplitType? = null,
    ): ProgramSkeleton {
        val days = daysPerWeek.coerceIn(2, 6)
        // ARCH #29: usa a escolha do usuário se for válida pro nº de dias; senão o recomendado (#26).
        val split = SplitCatalog.resolve(days, splitPreference)

        // 1. Alvo semanal por músculo (+ foco, respeitando o MRV).
        val focusTargets = focusMuscles.flatMap { VolumeTable.targetsForFocus(it) }.toSet()
        val weekly = TargetMuscle.entries.associateWith { m ->
            val base = VolumeTable.weeklySets(m, level, goal)
            val bonus = if (m in focusTargets) FOCUS_BONUS else 0
            minOf(base + bonus, VolumeTable.mrv(m))
        }

        // 2. Split → dias e seus músculos.
        val template = splitTemplate(days, split)

        // 3. Frequência = em quantos dias cada músculo aparece.
        val frequency = TargetMuscle.entries.associateWith { m ->
            template.count { (_, muscles) -> m in muscles }.coerceAtLeast(1)
        }

        // 4. Monta cada dia, respeitando o teto de exercícios por sessão.
        val daySkeletons = template.mapIndexed { dayIndex, pair ->
            val (label, muscles) = pair
            val trained = trainedMuscles(muscles, dayIndex, focusTargets)
            val sessionSets = trained.associateWith { m ->
                (weekly.getValue(m).toDouble() / frequency.getValue(m))
                    .roundToInt().coerceIn(SESSION_MIN_SETS, SESSION_MAX_SETS)
            }
            val exerciseCount = allocateExercises(trained, sessionSets)

            // Séries por exercício de cada músculo (3..5). Menos exercícios → mais séries.
            val setsPer = trained.associateWith { m ->
                (sessionSets.getValue(m).toDouble() / exerciseCount.getValue(m))
                    .roundToInt().coerceIn(SESSION_MIN_SETS, MAX_SETS_PER_EXERCISE)
            }.toMutableMap()

            // TETO DE VOLUME TOTAL: reduz séries dos músculos com mais séries até a sessão
            // caber no teto (piso de 3). Evita o "junk volume" (ex.: full body 2x com 30 séries).
            fun total() = trained.sumOf { exerciseCount.getValue(it) * setsPer.getValue(it) }
            while (total() > SESSION_MAX_SETS_TOTAL) {
                val alvo = trained.filter { setsPer.getValue(it) > SESSION_MIN_SETS }
                    .maxByOrNull { setsPer.getValue(it) } ?: break
                setsPer[alvo] = setsPer.getValue(alvo) - 1
            }

            val slots = trained.flatMap { m ->
                val sp = setsPer.getValue(m)
                (0 until exerciseCount.getValue(m)).map { i ->
                    val role = roleFor(m, i)
                    val p = RoleParams.paramsFor(role, level, goal)
                    Slot(m, role, sp, p.repRange, p.restSeconds, p.rir)
                }
            }.sortedBy { it.role.ordinal }   // compostos pesados primeiro no dia
            DaySkeleton(label, slots)
        }

        return ProgramSkeleton(daySkeletons, split.label, rationale(days, focusMuscles, split))
    }

    /**
     * Músculos treinados no dia respeitando o teto. Se cabem, todos entram. Se não
     * (full body = 9 músculos), mantém os compostos grandes e ROTACIONA os pequenos
     * entre os dias (cada dia cobre 1 pequeno diferente; o resto vem indireto dos compostos).
     */
    private fun trainedMuscles(
        muscles: Set<TargetMuscle>,
        dayIndex: Int,
        focus: Set<TargetMuscle>,
    ): List<TargetMuscle> {
        if (muscles.size <= SESSION_MAX_EXERCISES) {
            return canonicalOrder.filter { it in muscles }
        }
        val bigs = bigMuscles.filter { it in muscles }
        val smalls = smallMuscles.filter { it in muscles }
        val budgetForSmall = (SESSION_MAX_EXERCISES - bigs.size).coerceAtLeast(0)
        // FOCO PROTEGIDO (ARCH #26, defeito #1): músculo pequeno de foco entra SEMPRE,
        // antes da rotação — senão o bônus de volume do foco nunca vira exercício.
        val focusSmalls = smalls.filter { it in focus }
        val restSmalls = smalls.filter { it !in focus }
        val keptFocus = rotate(focusSmalls, dayIndex).take(budgetForSmall)
        val remaining = (budgetForSmall - keptFocus.size).coerceAtLeast(0)
        val keptRest = rotate(restSmalls, dayIndex).take(remaining)
        val kept = bigs.toSet() + keptFocus.toSet() + keptRest.toSet()
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
        TargetMuscle.CALVES, TargetMuscle.CORE,
        TargetMuscle.BICEPS, TargetMuscle.TRICEPS, TargetMuscle.FOREARMS -> SlotRole.ISOLAMENTO
        else -> when (index) {
            0 -> SlotRole.COMPOSTO_PESADO
            1 -> SlotRole.COMPOSTO_ACESSORIO
            else -> SlotRole.ISOLAMENTO
        }
    }

    // ---- split: em que dias cada músculo cai (frequência-primeiro, ~2x) ----

    private fun splitTemplate(days: Int, split: SplitType): List<Pair<String, Set<TargetMuscle>>> = when (split) {
        SplitType.FULL_BODY -> cycle(days, listOf("Full Body" to FULL))
        SplitType.UPPER_LOWER -> cycle(days, listOf("Upper" to UPPER, "Lower" to LOWER))
        SplitType.UPPER_LOWER_FULL -> listOf("Upper" to UPPER, "Lower" to LOWER, "Full Body" to FULL)
        SplitType.PUSH_PULL_LEGS -> cycle(days, listOf("Push" to PUSH, "Pull" to PULL, "Legs" to LEGS))
        SplitType.UL_PPL -> listOf(
            "Upper" to UPPER, "Lower" to LOWER, "Push" to PUSH, "Pull" to PULL, "Legs" to LEGS,
        )
        SplitType.ARNOLD -> cycle(
            days, listOf("Peito+Costas" to CHEST_BACK, "Pernas" to LEGS, "Ombros+Braços" to SHOULDERS_ARMS),
        )
    }

    /** Repete o ciclo base por `days`, sufixando A/B/... quando há mais de uma rodada. */
    private fun cycle(
        days: Int,
        base: List<Pair<String, Set<TargetMuscle>>>,
    ): List<Pair<String, Set<TargetMuscle>>> {
        val n = base.size
        val rounds = (days + n - 1) / n
        return (0 until days).map { i ->
            val (label, muscles) = base[i % n]
            val suffix = if (rounds > 1) " ${'A' + i / n}" else ""
            "$label$suffix" to muscles
        }
    }

    private fun rationale(days: Int, focus: Set<MuscleGroup>, split: SplitType): String {
        val base = "Split ${split.label} em $days dias: ${split.description} " +
            "O volume (séries/semana por músculo) é calibrado pelo seu nível — a base do modelo de hipertrofia."
        val f = if (focus.isEmpty()) "" else
            " Como você priorizou ${focus.joinToString(", ") { it.name }}, esses grupos recebem volume extra."
        return base + f
    }

    // ---- grupos de músculos por tipo de dia ----
    private val UPPER = setOf(TargetMuscle.CHEST, TargetMuscle.BACK, TargetMuscle.SHOULDERS, TargetMuscle.BICEPS, TargetMuscle.TRICEPS, TargetMuscle.FOREARMS)
    private val LOWER = setOf(TargetMuscle.QUADS, TargetMuscle.POSTERIOR, TargetMuscle.CALVES, TargetMuscle.GLUTES, TargetMuscle.CORE)
    private val PUSH = setOf(TargetMuscle.CHEST, TargetMuscle.SHOULDERS, TargetMuscle.TRICEPS)
    private val PULL = setOf(TargetMuscle.BACK, TargetMuscle.BICEPS, TargetMuscle.FOREARMS)
    private val LEGS = setOf(TargetMuscle.QUADS, TargetMuscle.POSTERIOR, TargetMuscle.CALVES, TargetMuscle.GLUTES)
    private val CHEST_BACK = setOf(TargetMuscle.CHEST, TargetMuscle.BACK)                 // Arnold dia 1
    private val SHOULDERS_ARMS = setOf(TargetMuscle.SHOULDERS, TargetMuscle.BICEPS, TargetMuscle.TRICEPS, TargetMuscle.FOREARMS)  // Arnold dia 3
    private val FULL = TargetMuscle.entries.toSet()

    /** Ordem canônica pra montagem determinística (grandes → pequenos). */
    private val canonicalOrder = listOf(
        TargetMuscle.CHEST, TargetMuscle.BACK, TargetMuscle.SHOULDERS,
        TargetMuscle.QUADS, TargetMuscle.POSTERIOR, TargetMuscle.GLUTES,
        TargetMuscle.BICEPS, TargetMuscle.TRICEPS, TargetMuscle.FOREARMS,
        TargetMuscle.CALVES, TargetMuscle.CORE,
    )

    // Prioridade quando o dia estoura o teto: compostos grandes sempre; pequenos rotacionam.
    private val bigMuscles = listOf(
        TargetMuscle.CHEST, TargetMuscle.BACK, TargetMuscle.QUADS,
        TargetMuscle.POSTERIOR, TargetMuscle.SHOULDERS,
    )
    private val smallMuscles = listOf(
        TargetMuscle.GLUTES, TargetMuscle.BICEPS, TargetMuscle.TRICEPS, TargetMuscle.FOREARMS,
        TargetMuscle.CALVES, TargetMuscle.CORE,
    )
}
