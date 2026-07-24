package dev.rafael.server.features.exercise.services

import dev.rafael.contract.exercise.ExerciseCategory
import dev.rafael.contract.exercise.ExerciseDto
import dev.rafael.contract.profile.BodyLimitation
import dev.rafael.contract.profile.Level
import dev.rafael.contract.profile.TrainingEnvironment
import dev.rafael.core.result.AppError
import dev.rafael.core.result.AppResult
import dev.rafael.core.result.asFailure
import dev.rafael.core.result.asSuccess
import dev.rafael.core.result.flatMap
import dev.rafael.core.result.map
import dev.rafael.server.features.exercise.db.ExerciseRepository
import dev.rafael.server.features.exercise.engine.ExercisePreFilter
import dev.rafael.server.features.exercise.models.toDto
import kotlin.uuid.Uuid

class ExerciseService(
    private val repository: ExerciseRepository,
    private val preFilter: ExercisePreFilter,
) {

    suspend fun listAll(): AppResult<List<ExerciseDto>> =
        repository.findAll().map { list -> list.map { it.toDto() } }

    suspend fun listByCategory(category: ExerciseCategory): AppResult<List<ExerciseDto>> =
        repository.findByCategory(category).map { list -> list.map { it.toDto() } }

    /**
     * Alternativas de mesma assinatura funcional (matching "balanceado", ARCH da troca):
     * mesmo movimento + composto/isolamento + músculo primário, dentro do ambiente/nível/
     * limitações do usuário (reusa o pré-filtro da geração). Exclui o próprio exercício.
     */
    suspend fun alternatives(
        exerciseId: Uuid,
        environment: TrainingEnvironment,
        level: Level,
        limitations: List<BodyLimitation>,
    ): AppResult<List<ExerciseDto>> =
        repository.findById(exerciseId).flatMap { target ->
            if (target == null) {
                AppError.NotFound("Exercício não encontrado").asFailure()
            } else {
                val pool = preFilter.poolFor(environment, limitations, level)
                pool.filter { c ->
                    c.id != exerciseId &&
                        c.movementPattern == target.movementPattern &&
                        c.isCompound == target.isCompound &&
                        target.primaryMuscles.any { it in c.primaryMuscles }
                }.map { it.toDto() }.asSuccess()
            }
        }
}
