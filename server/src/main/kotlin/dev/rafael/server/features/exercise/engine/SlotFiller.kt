package dev.rafael.server.features.exercise.engine

import dev.rafael.server.features.exercise.models.Exercise
import kotlin.random.Random
import kotlin.uuid.Uuid

/**
 * Preenchedor de slots (Fatia F.4). Para cada Slot, escolhe UM exercício do pool (F.3)
 * por pontuação, sem repetir no programa.
 *
 * Pontuação: foco > fit ao nível > composto-âncora > equipamento. Rotação DESEMPATA
 * (não domina). DETERMINÍSTICO (ARCH #20): mesma seed + mesmo perfil = mesmo treino.
 *
 * Controle de não-repetição usa Uuid (tipo nativo do Exercise.id); a conversão p/
 * String acontece só na fronteira (montagem do DTO).
 */
class SlotFiller(seed: Long) {

    private val rng = Random(seed)

    fun fillDay(
        day: DaySkeleton,
        pool: List<Exercise>,
        focusMuscles: Set<String>,
        userLevel: String,
        alreadyUsed: MutableSet<Uuid>,
    ): List<FilledExercise> {
        val out = mutableListOf<FilledExercise>()
        for (slot in day.slots) {
            val candidates = candidatesFor(slot, pool).filter { it.id !in alreadyUsed }
            val chosen = pickBest(candidates, slot, focusMuscles, userLevel) ?: continue
            alreadyUsed += chosen.id
            out += FilledExercise(chosen, slot)
        }
        return out
    }

    private fun candidatesFor(slot: Slot, pool: List<Exercise>): List<Exercise> = when (slot.kind) {
        SlotKind.COMPOUND -> pool.filter { it.isCompound == true && it.movementPattern == slot.pattern }
        SlotKind.ISOLATION -> pool.filter {
            it.isCompound == false && slot.muscle != null && slot.muscle in it.primaryMuscles
        }
        SlotKind.CORE -> pool.filter { ex -> ex.primaryMuscles.any { it.name == "CORE" } }
    }

    private fun pickBest(
        candidates: List<Exercise>,
        slot: Slot,
        focusMuscles: Set<String>,
        userLevel: String,
    ): Exercise? {
        if (candidates.isEmpty()) return null
        return candidates
            .maxByOrNull { score(it, slot, focusMuscles, userLevel) + rng.nextDouble() * ROTATION_JITTER }
    }

    private fun score(ex: Exercise, slot: Slot, focusMuscles: Set<String>, userLevel: String): Double {
        var s = 0.0
        if (ex.primaryMuscles.any { it.name in focusMuscles }) s += 4.0
        if (ex.level?.name == userLevel) s += 2.0   // Exercise.level é nullable
        if (slot.kind == SlotKind.COMPOUND && ex.isCompound == true) s += 2.0
        s += equipmentWeight(ex.equipment)
        return s
    }

    private fun equipmentWeight(equipment: String?): Double = when (equipment) {
        "BARBELL", "MACHINE", "CABLE" -> 1.0
        "DUMBBELL", "KETTLEBELL" -> 0.8
        "BAND", "GYMSTICK" -> 0.4
        "BODYWEIGHT", "IMPROVISED" -> 0.2
        else -> 0.5
    }

    private companion object { const val ROTATION_JITTER = 0.9 }
}

data class FilledExercise(val exercise: Exercise, val slot: Slot)