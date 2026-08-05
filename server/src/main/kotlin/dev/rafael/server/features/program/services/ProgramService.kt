package dev.rafael.server.features.program.services

import dev.rafael.contract.error.ErrorCodes
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
import dev.rafael.server.features.exercise.engine.WeekSpread
import dev.rafael.server.features.exercise.engine.WorkoutGenerator
import dev.rafael.server.features.program.db.ProgramRepository
import dev.rafael.server.features.program.models.Program
import dev.rafael.server.features.workout.models.Workout
import dev.rafael.server.features.workout.models.WorkoutExercise
import dev.rafael.server.features.workout.models.WorkoutSet
import kotlin.time.Clock
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.uuid.Uuid

/**
 * Orquestra programas (ARCH #22, revisado pela #27 — multi-programa, sem substituição).
 * O gate por teto/saúde fica na ROTA (padrão ARCH #18); aqui é gerar/criar + salvar.
 */
class ProgramService(
    private val generator: WorkoutGenerator,
    private val repository: ProgramRepository,
) {
    /** Contagem por origem (AI/MANUAL) — insumo dos gates de teto (ARCH #27) na rota. */
    suspend fun counts(userId: Uuid): AppResult<dev.rafael.server.features.program.models.ProgramCounts> =
        repository.counts(userId)

    /** Gera o programa determinístico e persiste (NÃO substitui os existentes — ARCH #27). */
    suspend fun generate(userId: Uuid, profile: ProfileDto): AppResult<ProgramDto> {
        val dto: ProgramDto = try {
            generator.generate(profile, prompt = null)   // motor determinístico ignora prompt
        } catch (e: IllegalArgumentException) {
            // política A: environment obrigatório
            return AppError.Validation(e.message ?: "Perfil incompleto para gerar programa.").asFailure()
        }

        val model = dto.toModel(userId, origin = WorkoutOrigin.AI, name = autoName(dto), unavailable = profile.unavailableDays.toSet())
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
            durationWeeks = PROGRAM_WEEKS, startedAt = ts,
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
     * Define o DIA da semana de cada treino (G.2 agenda por dia real). `entries` precisa cobrir
     * EXATAMENTE os treinos do programa; cada dia em 1..7 e DISTINTO (folga = dia sem treino).
     * Erros precisos: id inválido/dia fora de faixa/dia repetido/entries não batem → Validation;
     * programa não é do usuário → NotFound. O gate premium fica na ROTA (requireEditable, #18/#25).
     */
    suspend fun setSchedule(userId: Uuid, programId: Uuid, entries: List<ScheduleEntry>): AppResult<ProgramDto> {
        if (entries.isEmpty()) return AppError.Validation("A agenda não pode ser vazia").asFailure()
        if (entries.any { it.dayOfWeek !in 1..7 }) {
            return AppError.Validation("Dia da semana deve estar entre 1 (Seg) e 7 (Dom)").asFailure()
        }
        if (entries.map { it.dayOfWeek }.toSet().size != entries.size) {
            return AppError.Validation("Dois treinos não podem cair no mesmo dia").asFailure()
        }
        val parsed = entries.map { runCatching { Uuid.parse(it.workoutId) }.getOrNull() to it.dayOfWeek }
        if (parsed.any { it.first == null }) return AppError.Validation("workoutId inválido").asFailure()
        val byWorkout = parsed.mapNotNull { (id, day) -> id?.let { it to day } }.toMap()
        if (byWorkout.size != entries.size) {
            return AppError.Validation("A agenda não pode ter treino repetido").asFailure()
        }
        return repository.findByIdForUser(userId, programId).flatMap { program ->
            when {
                program == null -> AppError.NotFound("Programa não encontrado").asFailure()
                byWorkout.keys != program.workouts.map { it.id }.toSet() ->
                    AppError.Validation("A agenda precisa cobrir exatamente os treinos do programa").asFailure()
                else -> repository.setSchedule(userId, programId, byWorkout).flatMap { updated ->
                    if (updated == null) AppError.NotFound("Programa não encontrado").asFailure()
                    else updated.toDto().asSuccess()
                }
            }
        }
    }

    /**
     * Quantos treinos o programa já tem, validando posse — usado pela rota de
     * POST /workouts (ARCH #27) pra computar dayOfWeek sem workout→program depender
     * de ProgramRepository diretamente (evita ciclo de feature, ARCH #18).
     * null = programa não existe ou não é do usuário.
     */
    suspend fun workoutCountForOwner(userId: Uuid, programId: Uuid): AppResult<Int?> =
        repository.findByIdForUser(userId, programId).flatMap { it?.workouts?.size.asSuccess() }

    /** Origem do programa (AI/MANUAL) — usado pelo gate premium de edição (ARCH #25). null = não é do usuário. */
    suspend fun originOf(userId: Uuid, programId: Uuid): AppResult<WorkoutOrigin?> =
        repository.findByIdForUser(userId, programId).flatMap { it?.origin.asSuccess() }

    /**
     * Resolve o dia (1..7) de um treino NOVO no programa (G.2 — escolha do dia na criação).
     * - programa não é do usuário → NotFound
     * - dia escolhido livre → usa ele; fora de 1..7 ou já ocupado → Validation
     * - sem escolha (null) → primeiro dia livre (fallback; 1 se lotado)
     */
    suspend fun resolveNewWorkoutDay(userId: Uuid, programId: Uuid, chosen: Int?): AppResult<Int> =
        repository.findByIdForUser(userId, programId).flatMap { p ->
            if (p == null) {
                AppError.NotFound("Programa não encontrado").asFailure()
            } else {
                val used = p.workouts.mapNotNull { it.dayOfWeek }.toSet()
                when {
                    chosen == null -> ((1..7).firstOrNull { it !in used } ?: 1).asSuccess()
                    chosen !in 1..7 -> AppError.Validation("Dia da semana deve estar entre 1 (Seg) e 7 (Dom)").asFailure()
                    chosen in used -> AppError.Validation("Esse dia já tem um treino").asFailure()
                    else -> chosen.asSuccess()
                }
            }
        }

    /**
     * Gate premium de EDIÇÃO (ARCH #25): um programa origin=AI só pode ser MUTADO
     * (renomear, add/editar/remover treino) por usuário premium. Manual é livre.
     * Centraliza a regra que antes vivia inline no PUT /workouts. Descartar o programa
     * inteiro NÃO passa por aqui (é direito de descarte, fica livre).
     * - null   → NotFound (não é do usuário)
     * - AI+free → Forbidden(ENTITLEMENT_REQUIRED)
     * - resto  → Unit (segue a edição)
     */
    suspend fun requireEditable(userId: Uuid, programId: Uuid, isPremium: Boolean): AppResult<Unit> =
        originOf(userId, programId).flatMap { origin ->
            when {
                origin == null -> AppError.NotFound("Programa não encontrado").asFailure()
                origin == WorkoutOrigin.AI && !isPremium -> AppError.Forbidden(
                    "Editar um programa gerado por IA é um recurso premium.",
                    ErrorCodes.ENTITLEMENT_REQUIRED,
                ).asFailure()
                else -> Unit.asSuccess()
            }
        }

    private fun autoName(dto: ProgramDto): String = "Programa ${dto.daysPerWeek}x — ${dto.split}"
}

// ---- conversões ProgramDto (motor) <-> Program (model) ----

private const val PROGRAM_WEEKS = 8   // [INV] janela mínima/default do cronograma: 8 semanas (2 meses)

/** Semana atual (1..durationWeeks) derivada do início — autoridade do servidor, não do cliente. */
private fun currentWeekOf(startedAt: LocalDateTime, durationWeeks: Int): Int {
    val start = startedAt.toInstant(TimeZone.currentSystemDefault())
    val days = (Clock.System.now() - start).inWholeDays
    return (days / 7 + 1).toInt().coerceIn(1, durationWeeks)
}

private fun ProgramDto.toModel(userId: Uuid, origin: WorkoutOrigin, name: String, unavailable: Set<Int> = emptySet()): Program {
    val ts = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
    // Espaça os treinos pela semana (folga p/ recuperação), evitando os dias off do usuário.
    val days = WeekSpread.daysFor(workouts.size, split, unavailable)
    return Program(
        id = Uuid.NIL,            // repository gera o real
        userId = userId,
        name = name,
        origin = origin,
        daysPerWeek = daysPerWeek,
        split = split,
        rationale = rationale,
        locked = locked,
        workouts = workouts.mapIndexed { index, w ->
            Workout(
                id = Uuid.NIL, userId = userId, name = w.name, programId = null, dayOfWeek = days.getOrNull(index),
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
        durationWeeks = PROGRAM_WEEKS, startedAt = ts,
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
        // schedule: dia REAL da semana de cada treino (G.2 agenda por dia). Legado sem
        // dia cai na posição (i+1). A leitura já ordena por day_of_week.
        schedule = workouts.mapIndexed { i, w ->
            ScheduleEntry(workoutId = w.id.toString(), dayOfWeek = w.dayOfWeek ?: (i + 1))
        },
        durationWeeks = durationWeeks,
        startedAt = startedAt.toString(),
        currentWeek = currentWeekOf(startedAt, durationWeeks),
    )
}
