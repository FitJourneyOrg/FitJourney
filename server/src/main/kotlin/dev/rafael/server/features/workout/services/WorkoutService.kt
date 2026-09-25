package dev.rafael.server.features.workout.services

import dev.rafael.contract.profile.ProfileDto
import dev.rafael.contract.workout.WorkoutDto
import dev.rafael.contract.workout.WorkoutSummaryDto
import dev.rafael.contract.error.ErrorFields
import dev.rafael.core.result.AppError
import dev.rafael.core.result.AppResult
import dev.rafael.core.result.asFailure
import dev.rafael.core.result.asSuccess
import dev.rafael.core.result.flatMap
import dev.rafael.core.result.getOrNull
import dev.rafael.core.result.map
import dev.rafael.server.features.exercise.db.ExerciseRepository
import dev.rafael.server.features.exercise.engine.WorkoutGenerator
import dev.rafael.server.features.user.db.UserRepository
import dev.rafael.server.features.user.services.UserService
import dev.rafael.server.features.workout.db.WorkoutRepository
import dev.rafael.server.features.workout.models.toDomain
import dev.rafael.server.features.workout.models.toDto
import kotlin.uuid.Uuid
import dev.rafael.contract.error.ErrorCodes

class WorkoutService(
    private val userService: UserService,
    private val repository: WorkoutRepository,
    private val exerciseRepository: ExerciseRepository,
    private val generator: WorkoutGenerator,          // <- única dep nova
    private val userRepository: UserRepository,       // <- V59: dono do ponteiro "ativo"
) {

    /**
     * programId/dayOfWeek (ARCH #27): resolvidos e validados na ROTA (não aqui) —
     * validar posse do programa exigiria WorkoutService conhecer ProgramRepository,
     * o que criaria um ciclo workout→program (program já depende de workout).
     * Gate composto na rota, como manda o ARCH #18.
     */
    suspend fun create(
        firebaseUid: String,
        email: String?,
        dto: WorkoutDto,
        programId: Uuid,
        dayOfWeek: Int,
    ): AppResult<WorkoutDto> {
        validate(dto)?.let { return it.asFailure() }
        validateExercisesExist(dto)?.let { return it.asFailure() }
        return userService.findOrCreate(firebaseUid, email).flatMap { user ->
            repository.create(user.id, dto.toDomain(), programId, dayOfWeek).flatMap { criado ->
                // null = id do cliente colidiu com treino de OUTRO usuário. Não é erro de
                // servidor (500) nem "não encontrado": é conflito de identificador.
                criado?.toDto()?.asSuccess()
                    ?: AppError.Conflict(
                        "Não consegui salvar este treino. Tente criar de novo.",
                        code = ErrorCodes.TREINO_DUPLICADO,
                    ).asFailure()
            }
        }
    }

    suspend fun list(firebaseUid: String, email: String?): AppResult<List<WorkoutSummaryDto>> =
        userService.findOrCreate(firebaseUid, email).flatMap { user ->
            // V59: isActive é comparação simples contra o ponteiro do usuário, não estado
            // guardado por treino — não precisa join nem coluna nova em workouts.
            repository.findAllByUser(user.id)
                .map { list -> list.map { it.toDto(isActive = it.id == user.activeWorkoutId) } }
        }

    /**
     * V59. Marca este treino como o ativo do usuário (ponteiro simples, exclusivo — substitui
     * o anterior). Não mexe em sessão/histórico: "sessões desta semana" continua sendo derivado
     * de `workout_sessions`, então trocar de ativo nunca zera nem precisa pausar nada.
     *
     * Reaproveita o `findById` (já filtra por `user.id`) pra checar posse — treino que não
     * existe OU que não é deste usuário caem no mesmo NotFound, sem vazar diferença.
     */
    suspend fun activate(firebaseUid: String, email: String?, workoutId: Uuid): AppResult<Unit> =
        userService.findOrCreate(firebaseUid, email).flatMap { user ->
            repository.findById(user.id, workoutId).flatMap { treino ->
                if (treino == null) {
                    AppError.NotFound("Treino não encontrado", code = ErrorCodes.TREINO_NAO_EXISTE).asFailure()
                } else {
                    userRepository.setActiveWorkout(user.id, workoutId).map { }
                }
            }
        }

    suspend fun get(firebaseUid: String, email: String?, workoutId: Uuid): AppResult<WorkoutDto?> =
        userService.findOrCreate(firebaseUid, email).flatMap { user ->
            repository.findById(user.id, workoutId).map { it?.toDto() }
        }

    suspend fun update(firebaseUid: String, email: String?, workoutId: Uuid, dto: WorkoutDto): AppResult<WorkoutDto?> {
        validate(dto)?.let { return it.asFailure() }
        validateExercisesExist(dto)?.let { return it.asFailure() }
        return userService.findOrCreate(firebaseUid, email).flatMap { user ->
            repository.update(user.id, workoutId, dto.toDomain()).map { it?.toDto() }
        }
    }

    suspend fun delete(firebaseUid: String, email: String?, workoutId: Uuid): AppResult<Boolean> =
        userService.findOrCreate(firebaseUid, email).flatMap { user ->
            repository.delete(user.id, workoutId)
        }

    private fun validate(dto: WorkoutDto): AppError? {
        if (dto.name.isBlank()) return AppError.Validation("Nome do treino é obrigatório", mapOf(ErrorFields.NAME to "Nome do treino é obrigatório"), code = ErrorCodes.NOME_DE_TREINO_VAZIO)
        if (dto.exercises.isEmpty()) return AppError.Validation("Treino precisa de ao menos 1 exercício", mapOf(ErrorFields.EXERCISES to "Treino precisa de ao menos 1 exercício"), code = ErrorCodes.TREINO_SEM_EXERCICIO)
        dto.exercises.forEach { ex ->
            if (ex.sets.isEmpty()) return AppError.Validation("Cada exercício precisa de ao menos 1 série", mapOf(ErrorFields.SETS to "Cada exercício precisa de ao menos 1 série"), code = ErrorCodes.EXERCICIO_SEM_SERIE)
            ex.sets.forEach { s ->
                if (s.reps <= 0) return AppError.Validation("Repetições devem ser maiores que zero", mapOf(ErrorFields.REPS to "Repetições devem ser maiores que zero"), code = ErrorCodes.REPETICOES_INVALIDAS)
            }
        }
        return null
    }

    private suspend fun validateExercisesExist(dto: WorkoutDto): AppError? {
        val ids = try {
            dto.exercises.map { Uuid.parse(it.exerciseId) }
        } catch (e: IllegalArgumentException) {
            return AppError.Validation("Um dos exercícios deste treino não pôde ser salvo.", code = ErrorCodes.ID_DE_EXERCICIO_INVALIDO)
        }
        val allExist = exerciseRepository.existsByIds(ids).getOrNull() ?: false
        return if (allExist) null
        else AppError.Validation("Um ou mais exercícios não existem no catálogo", code = ErrorCodes.EXERCICIO_FORA_DO_CATALOGO)
    }
}