package dev.rafael.server.features.exercise.engine

import dev.rafael.contract.exercise.ExerciseCategory
import dev.rafael.contract.profile.MuscleGroup
import dev.rafael.server.features.exercise.models.Exercise
import dev.rafael.server.features.exercise.models.MovementPattern
import kotlin.random.Random
import kotlin.uuid.Uuid

/**
 * Preenchedor de slots (F.4, ajustado no ARCH #27). Para cada Slot, escolhe UM
 * exercício do pool que casa com o músculo-alvo e o papel (composto vs isolamento),
 * sem repetir no programa. Determinístico (mesma seed + perfil = mesmo treino).
 *
 * Perna fina: QUADS/POSTERIOR casam por movement_pattern (dado confiável); os demais
 * por primary_muscles; panturrilha por category CALVES.
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

    private fun candidatesFor(slot: Slot, pool: List<Exercise>): List<Exercise> {
        val byMuscle = pool.filter { matchesTarget(it, slot.target) }
        return when (slot.role) {
            SlotRole.COMPOSTO_PESADO, SlotRole.COMPOSTO_ACESSORIO -> byMuscle.filter { it.isCompound == true }
            SlotRole.ISOLAMENTO -> byMuscle.filter { it.isCompound == false }
        }
    }

    private fun matchesTarget(ex: Exercise, target: TargetMuscle): Boolean = when (target) {
        TargetMuscle.CHEST -> MuscleGroup.CHEST in ex.primaryMuscles
        TargetMuscle.BACK -> MuscleGroup.BACK in ex.primaryMuscles
        TargetMuscle.SHOULDERS -> MuscleGroup.SHOULDERS in ex.primaryMuscles
        TargetMuscle.ARMS -> MuscleGroup.ARMS in ex.primaryMuscles
        TargetMuscle.QUADS -> ex.movementPattern in QUAD_PATTERNS
        // Posterior (isquios): padrão de perna E primário LEGS — exclui hip thrust (primário GLUTES).
        TargetMuscle.POSTERIOR -> ex.movementPattern in POSTERIOR_PATTERNS && MuscleGroup.LEGS in ex.primaryMuscles
        TargetMuscle.CALVES -> ex.category == ExerciseCategory.CALVES
        // Glúteo: só primário GLUTES (removido o fallback HINGE que roubava o hip thrust do posterior).
        TargetMuscle.GLUTES -> MuscleGroup.GLUTES in ex.primaryMuscles
        TargetMuscle.CORE -> MuscleGroup.CORE in ex.primaryMuscles || ex.category == ExerciseCategory.CORE
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
        if (ex.level?.name == userLevel) s += 2.0
        if (slot.role != SlotRole.ISOLAMENTO && ex.isCompound == true) s += 2.0
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

    private companion object {
        const val ROTATION_JITTER = 0.9
        val QUAD_PATTERNS = setOf(MovementPattern.SQUAT, MovementPattern.LUNGE, MovementPattern.KNEE_EXTENSION)
        val POSTERIOR_PATTERNS = setOf(MovementPattern.HINGE, MovementPattern.KNEE_FLEXION)
    }
}

data class FilledExercise(val exercise: Exercise, val slot: Slot)
