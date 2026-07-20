package dev.rafael.server.features.exercise.engine

import dev.rafael.contract.profile.Goal
import dev.rafael.contract.profile.Level
import dev.rafael.contract.profile.MuscleGroup
import dev.rafael.server.features.exercise.models.MovementPattern

/**
 * Motor de estrutura (Fatia F.2). Monta o ESQUELETO do programa a partir da
 * parametrização decidida (ARCH #26), ancorada em ACSM/NSCA/Schoenfeld + einstein 2021.
 *
 * Volume é a variável-mãe: ditado pelo nível (segurança), não pelo goal. O split deriva
 * dos dias pra garantir ~2x/músculo (frequência-primeiro). Foco só p/ INTER/ADVANCED.
 * Valores [PROPOSTA] até validar em uso — centralizados aqui.
 */
class StructureEngine {

    private data class LevelParams(
        val setsPerExercise: Int,
        val repRange: String,
        val restSeconds: Int,
    )

    private fun paramsFor(level: Level, goal: Goal): LevelParams {
        if (goal == Goal.GENERAL_HEALTH) return LevelParams(2, "8-15", 75)
        return when (level) {
            Level.BEGINNER -> LevelParams(3, "8-12", 75)
            Level.INTERMEDIATE -> LevelParams(3, "8-12", 105)
            Level.ADVANCED -> LevelParams(4, "8-12", 150)
        }
    }

    fun buildSkeleton(
        goal: Goal,
        level: Level,
        daysPerWeek: Int,
        focusMuscles: Set<MuscleGroup>,
    ): ProgramSkeleton {
        val p = paramsFor(level, goal)
        val days = daysPerWeek.coerceIn(2, 6)
        val templates = splitTemplates(days)

        val daySkeletons = templates.map { template ->
            val baseSlots = template.patterns.map { pattern ->
                Slot(SlotKind.COMPOUND, pattern = pattern, sets = p.setsPerExercise,
                    repRange = p.repRange, restSeconds = p.restSeconds)
            }
            val focus = focusMuscles
                .filter { it in template.focusableMuscles }
                .map { m ->
                    Slot(SlotKind.ISOLATION, muscle = m, sets = p.setsPerExercise,
                        repRange = p.repRange, restSeconds = p.restSeconds, isFocus = true)
                }
            DaySkeleton(label = template.label, slots = baseSlots + focus)
        }

        return ProgramSkeleton(daySkeletons, splitName(days), rationale(days, focusMuscles))
    }

    // ---- split derivado dos dias (frequência-primeiro) ----

    private fun splitTemplates(days: Int): List<DayTemplate> = when (days) {
        2 -> listOf(fullBody("A"), fullBody("B"))
        3 -> listOf(fullBody("A"), fullBody("B"), fullBody("C"))
        4 -> listOf(upper("Upper A"), lower("Lower A"), upper("Upper B"), lower("Lower B"))
        5 -> listOf(upper("Upper"), lower("Lower"), push("Push"), pull("Pull"), legs("Legs"))
        else -> listOf(push("Push A"), pull("Pull A"), legs("Legs A"),
            push("Push B"), pull("Pull B"), legs("Legs B"))
    }

    private fun splitName(days: Int) = when (days) {
        2, 3 -> "Full Body"; 4 -> "Upper/Lower"; 5 -> "Upper/Lower + PPL"; else -> "Push/Pull/Legs"
    }

    private fun rationale(days: Int, focus: Set<MuscleGroup>): String {
        val base = when (days) {
            2, 3 -> "Montamos Full Body porque você treina $days dias — cada grupo é estimulado com mais " +
                    "frequência, o que a ciência mostra ser mais eficaz em baixa frequência."
            4 -> "Com 4 dias, Upper/Lower: cada músculo 2x por semana, o ponto ideal para hipertrofia."
            5 -> "Com 5 dias, Upper/Lower + Push/Pull/Legs para manter frequência 2x e distribuir o volume."
            else -> "Com 6 dias, PPL repetido: cada músculo 2x por semana, volume alto bem distribuído."
        }
        return if (focus.isEmpty()) base
        else base + " Como você priorizou ${focus.joinToString(", ") { it.name }}, esses grupos recebem volume extra."
    }

    // ---- templates de dia (padrões compostos; a F.4 preenche) ----

    private fun fullBody(label: String) = DayTemplate(label, listOf(
        MovementPattern.HORIZONTAL_PUSH, MovementPattern.HORIZONTAL_PULL,
        MovementPattern.SQUAT, MovementPattern.HINGE, MovementPattern.VERTICAL_PUSH,
    ), allMuscles)

    private fun upper(label: String) = DayTemplate(label, listOf(
        MovementPattern.HORIZONTAL_PUSH, MovementPattern.VERTICAL_PUSH,
        MovementPattern.HORIZONTAL_PULL, MovementPattern.VERTICAL_PULL,
    ), upperMuscles)

    private fun lower(label: String) = DayTemplate(label, listOf(
        MovementPattern.SQUAT, MovementPattern.HINGE,
    ), lowerMuscles)

    private fun push(label: String) = DayTemplate(label, listOf(
        MovementPattern.HORIZONTAL_PUSH, MovementPattern.VERTICAL_PUSH,
    ), pushMuscles)

    private fun pull(label: String) = DayTemplate(label, listOf(
        MovementPattern.VERTICAL_PULL, MovementPattern.HORIZONTAL_PULL,
    ), pullMuscles)

    private fun legs(label: String) = DayTemplate(label, listOf(
        MovementPattern.SQUAT, MovementPattern.HINGE,
    ), lowerMuscles)

    // MuscleGroup real: CHEST, BACK, ARMS, SHOULDERS, LEGS, GLUTES, CORE
    private val upperMuscles = setOf(MuscleGroup.CHEST, MuscleGroup.BACK, MuscleGroup.SHOULDERS, MuscleGroup.ARMS)
    private val pushMuscles = setOf(MuscleGroup.CHEST, MuscleGroup.SHOULDERS, MuscleGroup.ARMS)
    private val pullMuscles = setOf(MuscleGroup.BACK, MuscleGroup.ARMS)
    private val lowerMuscles = setOf(MuscleGroup.LEGS, MuscleGroup.GLUTES)
    private val allMuscles = MuscleGroup.entries.toSet()

    private data class DayTemplate(
        val label: String,
        val patterns: List<MovementPattern>,
        val focusableMuscles: Set<MuscleGroup>,
    )
}