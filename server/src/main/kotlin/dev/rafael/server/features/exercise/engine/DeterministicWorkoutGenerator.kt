package dev.rafael.server.features.exercise.engine

import dev.rafael.contract.profile.Level
import dev.rafael.contract.profile.ProfileDto
import dev.rafael.contract.profile.TrainingEnvironment
import dev.rafael.contract.program.ProgramDto
import dev.rafael.contract.workout.WorkoutDto
import dev.rafael.contract.workout.WorkoutExerciseDto
import dev.rafael.contract.workout.WorkoutOrigin
import dev.rafael.contract.workout.WorkoutSetDto
import dev.rafael.server.features.exercise.models.Exercise
import kotlin.uuid.Uuid

/**
 * Motor de geração DETERMINÍSTICO (Fatia F.4, ARCH #20/#22). Implementa WorkoutGenerator.
 *
 * Fluxo: F.2 (StructureEngine → esqueleto) → F.3 (ExercisePreFilter → pool) →
 *        F.4 (SlotFiller → preenche). Zero LLM. Reproduzível por seed.
 *
 * Retorna ProgramDto (ARCH #22 — programa semanal, N treinos).
 *
 * Política de environment (decisão A): environment é nullable no ProfileDto mas é
 * OBRIGATÓRIO pra gerar (o pré-filtro precisa dele). Se null, lança
 * IllegalArgumentException — a rota traduz em 400. O motor não adivinha ambiente.
 */
class DeterministicWorkoutGenerator(
    private val structureEngine: StructureEngine,
    private val preFilter: ExercisePreFilter,
) : WorkoutGenerator {

    override suspend fun generate(profile: ProfileDto, prompt: String?): ProgramDto {
        // prompt: ignorado — motor determinístico não usa texto livre (ARCH #20).

        // Política A: exige environment (não adivinha).
        val environment: TrainingEnvironment = requireNotNull(profile.environment) {
            "Ambiente de treino não definido — necessário para gerar o programa."
        }

        // seed derivada do perfil → determinístico sem poluir a assinatura.
        val seed = deriveSeed(profile)

        // 1. ESQUELETO (F.2) — foco só p/ INTER/ADVANCED (exceto GENERAL_HEALTH).
        val focus = effectiveFocus(profile)
        val skeleton = structureEngine.buildSkeleton(
            goal = profile.goal, level = profile.level,
            daysPerWeek = profile.daysPerWeek, focusMuscles = focus,
        )

        // 2. POOL (F.3) — ambiente + limitações + nível.
        val pool: List<Exercise> = preFilter.poolFor(environment, profile.limitations, profile.level)

        // 3. PREENCHER (F.4) — pontuação + rotação, sem repetir no programa.
        val filler = SlotFiller(seed)
        val used = mutableSetOf<Uuid>()
        val focusNames = focus.map { it.name }.toSet()

        val workouts = skeleton.days.mapIndexed { index, day ->
            val filled = filler.fillDay(day, pool, focusNames, profile.level.name, used)
            WorkoutDto(
                name = day.label,
                origin = WorkoutOrigin.AI,
                exercises = filled.mapIndexed { i, fe -> fe.toExerciseDto(i) },
            )
        }

        return ProgramDto(
            workouts = workouts,
            daysPerWeek = profile.daysPerWeek,
            split = skeleton.split,
            rationale = skeleton.rationale,
            locked = false,   // posse/blur é decidido na rota/service (ARCH #23), não no motor
        )
    }

    /** Foco só vale p/ INTER/ADVANCED e nunca p/ GENERAL_HEALTH (máx 2 grupos). */
    private fun effectiveFocus(profile: ProfileDto): Set<dev.rafael.contract.profile.MuscleGroup> {
        val eligible = profile.level != Level.BEGINNER &&
                profile.goal != dev.rafael.contract.profile.Goal.GENERAL_HEALTH
        return if (eligible) profile.focusAreas.take(2).toSet() else emptySet()
    }

    /** Seed reproduzível a partir do perfil (mesmo perfil = mesmo treino). */
    private fun deriveSeed(profile: ProfileDto): Long {
        // combina campos estáveis do perfil; determinístico e testável.
        var h = 17L
        h = 31 * h + profile.goal.ordinal
        h = 31 * h + profile.level.ordinal
        h = 31 * h + profile.daysPerWeek
        h = 31 * h + profile.focusAreas.sumOf { it.ordinal + 1 }
        h = 31 * h + (profile.environment?.ordinal ?: 0)
        return h
    }
}

/** Converte o slot preenchido em WorkoutExerciseDto (com séries e descanso da F.2). */
private fun FilledExercise.toExerciseDto(orderIndex: Int): WorkoutExerciseDto {
    val reps = midReps(slot.repRange)
    return WorkoutExerciseDto(
        exerciseId = exercise.id.toString(),
        orderIndex = orderIndex,
        restSeconds = slot.restSeconds,
        sets = (0 until slot.sets).map { WorkoutSetDto(reps = reps, orderIndex = it) },
    )
}

/** Faixa "8-12" → meio (10), gravado como Int (a faixa é orientação; execução é exata). */
private fun midReps(range: String): Int {
    val parts = range.split("-").mapNotNull { it.trim().toIntOrNull() }
    return if (parts.size == 2) (parts[0] + parts[1]) / 2 else (parts.firstOrNull() ?: 10)
}