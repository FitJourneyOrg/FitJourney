package dev.rafael.features.program.domain.model

/**
 * Programa (ARCH #22, revisado pela #26 — usuário pode ter vários, não é mais
 * 1 ativo que se substitui). Agrupa N treinos.
 */
data class Program(
    val id: String?,               // null só em estados transitórios; server sempre preenche na resposta
    val name: String,
    val workouts: List<ProgramWorkout>,
    val daysPerWeek: Int,
    val split: String,
    val rationale: String,
    val locked: Boolean,
    val schedule: List<ProgramScheduleEntry>,
    val createdAt: String?,
    val updatedAt: String?,
)

/**
 * Treino dentro de um programa — versão enxuta (não reusa workout:domain.Workout de
 * propósito: Konsist trata domain de todas as features como 1 camada única e proíbe
 * dependência entre elas). Detalhe completo (exercícios/séries) mora na feature workout;
 * a tela navega pro WorkoutDetailScreen pra ver isso.
 */
data class ProgramWorkout(
    val id: String?,
    val name: String,
    val exerciseCount: Int,
)

data class ProgramScheduleEntry(
    val workoutId: String,
    val dayOfWeek: Int,
)
