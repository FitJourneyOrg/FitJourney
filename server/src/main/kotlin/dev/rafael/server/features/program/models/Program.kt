package dev.rafael.server.features.program.models

import dev.rafael.contract.workout.WorkoutOrigin
import dev.rafael.server.features.workout.models.Workout
import kotlin.uuid.Uuid
import kotlinx.datetime.LocalDateTime

/**
 * Programa semanal (ARCH #22, revisado pela #26). Agrupa N treinos (Workout com
 * program_id). Usuário pode ter vários programas — não é mais 1 ativo que se
 * substitui. Tetos por plano (ver ProgramCounts): grátis = 1 IA + 2 manuais;
 * premium = 10 no total.
 * Sem default (convenção pós-bugs: o compilador cobra em cada construção).
 */
data class Program(
    val id: Uuid,
    val userId: Uuid,
    val name: String,
    val origin: WorkoutOrigin,
    val daysPerWeek: Int,
    val split: String,
    val rationale: String,
    val locked: Boolean,
    val workouts: List<Workout>,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
)

/** Contagem de programas por origem — insumo dos gates de teto (ARCH #26). */
data class ProgramCounts(val ai: Int, val manual: Int) {
    val total: Int get() = ai + manual
}
