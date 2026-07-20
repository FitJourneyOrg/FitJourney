package dev.rafael.contract.program

import dev.rafael.contract.workout.WorkoutDto
import kotlinx.serialization.Serializable

/**
 * Programa semanal gerado pelo motor (ARCH #22). N treinos (A/B/C conforme o split),
 * agendáveis e — se premium — editáveis. A rota /programs/generate retorna isto.
 *
 * Blur/posse (ARCH #23): quando locked=true, os workouts de índice > 0 vêm como
 * placeholder (o conteúdo real não trafega). O motor devolve locked=false; quem decide
 * a posse é o service/rota conforme o entitlement.
 */
@Serializable
data class ProgramDto(
    val id: String? = null,               // servidor preenche
    val workouts: List<WorkoutDto> = emptyList(),
    val daysPerWeek: Int,
    val split: String,                    // ex.: "Upper/Lower + PPL"
    val rationale: String,                // explicação do híbrido, mostrada na revelação
    val locked: Boolean = false,
    val schedule: List<ScheduleEntry> = emptyList(),
    val createdAt: String? = null,
    val updatedAt: String? = null,
)

@Serializable
data class ScheduleEntry(
    val workoutId: String,
    val dayOfWeek: Int,                   // 1=segunda ... 7=domingo
)