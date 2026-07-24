package dev.rafael.server.features.program.services

import dev.rafael.contract.profile.ProfileDto
import dev.rafael.contract.program.ProgramDto
import dev.rafael.contract.program.ScheduleEntry
import dev.rafael.contract.workout.WorkoutDto
import dev.rafael.contract.workout.WorkoutExerciseDto
import dev.rafael.contract.workout.WorkoutOrigin
import dev.rafael.contract.workout.WorkoutSetDto
import dev.rafael.core.result.AppError
import dev.rafael.core.result.AppResult
import dev.rafael.core.result.asFailure
import dev.rafael.core.result.asSuccess
import dev.rafael.core.result.flatMap
import dev.rafael.server.features.exercise.engine.WorkoutGenerator
import dev.rafael.server.features.program.db.ProgramRepository
import dev.rafael.server.features.program.models.Program
import dev.rafael.server.features.workout.models.Workout
import dev.rafael.server.features.workout.models.WorkoutExercise
import dev.rafael.server.features.workout.models.WorkoutSet
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.uuid.Uuid

/**
 * Orquestra programas (ARCH #22, revisado pela #26 — multi-programa, sem substituição).
 * O gate por teto/saúde fica na ROTA (padrão ARCH #18); aqui é gerar/criar + salvar.
 */
class ProgramService(
    private val generator: WorkoutGenerator,
    private val repository: ProgramRepository,
) {
    /** Contagem por origem (AI/MANUAL) — insumo dos gates de teto (ARCH #26) na rota. */
    suspend fun counts(userId: Uuid): AppResult<dev.rafael.server.features.program.models.ProgramCounts> =
        repository.counts(userId)

    /** Gera o programa determinístico e persiste (NÃO substitui os existentes — ARCH #26). */
    suspend fun generate(userId: Uuid, profile: ProfileDto): AppResult<ProgramDto> {
        val dto: ProgramDto = try {
            generator.generate(profile, prompt = null)   // motor determinístico ignora prompt
        } catch (e: IllegalArgumentException) {
            // política A: environment obrigatório
            return AppError.Validation(e.message ?: "Perfil incompleto para gerar programa.").asFailure()
        }

        val model = dto.toModel(userId, origin = WorkoutOrigin.AI, name = autoName(dto))
        return repository.createForUser(userId, model).flatMap { saved ->
            saved.toDto().asSuccess()
        }
    }

    /** Cria um programa vazio (sem motor) só pra abrigar treino avulso. Não conta no teto. */
    suspend fun createManual(userId: Uuid, name: String): AppResult<ProgramDto> {
        if (name.isBlank()) return AppError.Validation("Nome do programa é obrigatório").asFailure()
        val ts = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        val shell = Program(
            id = Uuid.NIL, userId = userId, name = name, origin = WorkoutOrigin.MANUAL,
            daysPerWeek = 0, split = "Manual", rationale = "", locked = false,
            workouts = emptyList(), createdAt = ts, updatedAt = ts,
        )
        return repository.createForUser(userId, shell).flatMap { it.toDto().asSuccess() }
    }

    suspend fun listForUser(userId: Uuid): AppResult<List<ProgramDto>> =
        repository.findAllByUser(userId).flatMap { it.map { p -> p.toDto() }.asSuccess() }

    suspend fun rename(userId: Uuid, programId: Uuid, name: String): AppResult<ProgramDto> {
        if (name.isBlank()) return AppError.Validation("Nome do programa é obrigatório").asFailure()
        return repository.rename(userId, programId, name).flatMap { updated ->
            if (updated == null) AppError.NotFound("Programa não encontrado").asFailure()
            else updated.toDto().asSuccess()
        }
    }

    /** Remove o programa do usuário (CASCADE apaga os treinos). false → rota devolve NotFound. */
    suspend fun delete(userId: Uuid, programId: Uuid): AppResult<Boolean> =
        repository.delete(userId, programId)

    /**
     * Quantos treinos o programa já tem, validando posse — usado pela rota de
     * POST /workouts (ARCH #26) pra computar dayOfWeek sem workout→program depender
     * de ProgramRepository diretamente (evita ciclo de feature, ARCH #18).
     * null = programa não existe ou não é do usuário.
     */
    suspend fun workoutCountForOwner(userId: Uuid, programId: Uuid): AppResult<Int?> =
        repository.findByIdForUser(userId, programId).flatMap { it?.workouts?.size.asSuccess() }

    private fun autoName(dto: ProgramDto): String = "Programa ${dto.daysPerWeek}x — ${dto.split}"
}

// ---- conversões ProgramDto (motor) <-> Program (model) ----

private fun ProgramDto.toModel(userId: Uuid, origin: WorkoutOrigin, name: String): Program {
    val ts = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
    return Program(
        id = Uuid.NIL,            // repository gera o real
        userId = userId,
        name = name,
        origin = origin,
        daysPerWeek = daysPerWeek,
        split = split,
        rationale = rationale,
        locked = locked,
        workouts = workouts.map { w ->
            Workout(
                id = Uuid.NIL, userId = userId, name = w.name, programId = null,   // repository seta ao inserir
                exercises = w.exercises.map { e ->
                    WorkoutExercise(
                        id = Uuid.NIL,
                        exerciseId = Uuid.parse(e.exerciseId),
                        orderIndex = e.orderIndex,
                        restSeconds = e.restSeconds,
                        rir = e.rir,
                        sets = e.sets.map { s -> WorkoutSet(Uuid.NIL, s.reps, s.orderIndex) },
                    )
                },
                createdAt = ts, updatedAt = ts,
            )
        },
        createdAt = ts, updatedAt = ts,
    )
}

// CORREÇÃO do Program.toDto() — popular o schedule (estava saindo vazio).
// O schedule diz em que dia cada treino cai. No v1, deriva do índice
// (o repository gravou day_of_week = index + 1; o cliente reordena na G.2).

private fun Program.toDto(): ProgramDto {
    val workoutDtos = workouts.map { w ->
        WorkoutDto(
            id = w.id.toString(),
            name = w.name,
            origin = origin,
            programId = id.toString(),
            exercises = w.exercises.map { e ->
                WorkoutExerciseDto(
                    exerciseId = e.exerciseId.toString(),
                    orderIndex = e.orderIndex,
                    restSeconds = e.restSeconds,
                    rir = e.rir,
                    sets = e.sets.map { s ->
                        WorkoutSetDto(reps = s.reps, orderIndex = s.orderIndex)
                    },
                )
            },
        )
    }

    return ProgramDto(
        id = id.toString(),
        name = name,
        origin = origin,
        workouts = workoutDtos,
        daysPerWeek = daysPerWeek,
        split = split,
        rationale = rationale,
        locked = locked,
        // schedule: i-ésimo treino cai no dia i+1 (v1 sequencial; reordena na G.2).
        schedule = workoutDtos.mapIndexed { i, w ->
            ScheduleEntry(workoutId = w.id!!, dayOfWeek = i + 1)
        },
    )
}
