package dev.rafael.server.features.workout.ai

import dev.rafael.contract.profile.ProfileDto
import dev.rafael.contract.workout.WorkoutDto
import dev.rafael.contract.workout.WorkoutExerciseDto
import dev.rafael.contract.workout.WorkoutOrigin
import dev.rafael.contract.workout.WorkoutSetDto
import dev.rafael.core.result.getOrNull
import dev.rafael.server.features.exercise.db.ExerciseRepository
import dev.rafael.server.features.profile.models.Profile

/**
 * Gerador STUB — heurística determinística, sem LLM. Só prova o fluxo
 * (gate → gera → valida → persiste). Trocável pela impl LLM depois (mesma interface).
 * Escolhe exercícios REAIS do catálogo (senão a validação rejeita).
 */
class StubWorkoutGenerator(
    private val exerciseRepository: ExerciseRepository,
) : WorkoutGenerator {

    override suspend fun generate(profile: ProfileDto, prompt: String?): WorkoutDto {
        // pega exercícios do catálogo. ⚠️ CONFIRMAR nome do método (findAll?)
        val catalog = exerciseRepository.findAll().getOrNull().orEmpty()

        // heurística boba: nº de exercícios ~ daysPerWeek; 3 séries de 12.
        val count = (profile.daysPerWeek + 2).coerceIn(3, 8)
        val chosen = catalog.take(count)

        val exercises = chosen.mapIndexed { i, ex ->
            WorkoutExerciseDto(
                exerciseId = ex.id.toString(),   // ⚠️ CONFIRMAR: ex.id é Uuid? então toString()
                orderIndex = i,
                sets = List(3) { j -> WorkoutSetDto(reps = 12, orderIndex = j) },
            )
        }

        return WorkoutDto(
            id = null,
            name = "Treino IA — ${profile.goal.name.lowercase().replaceFirstChar { it.uppercase() }}",
            origin = WorkoutOrigin.AI,
            exercises = exercises,
            createdAt = null,
            updatedAt = null,
        )
    }
}