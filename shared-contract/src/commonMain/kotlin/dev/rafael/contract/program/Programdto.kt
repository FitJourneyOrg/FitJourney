package dev.rafael.contract.program

import dev.rafael.contract.workout.WorkoutDto
import dev.rafael.contract.workout.WorkoutOrigin
import kotlinx.serialization.Serializable

/**
 * Programa semanal (ARCH #22). N treinos (A/B/C conforme o split), agendáveis e —
 * se premium — editáveis. A rota /programs/generate cria um de origin=AI;
 * POST /programs cria um "shell" vazio de origin=MANUAL pra abrigar treino avulso.
 *
 * Multi-programa (ARCH #27): usuário pode ter vários programas (não é mais 1 ativo
 * que se substitui). O teto de geração é por contagem de origin=AI — ver ProgramService.
 *
 * Blur/posse (ARCH #23): quando locked=true, os workouts de índice > 0 vêm como
 * placeholder (o conteúdo real não trafega). O motor devolve locked=false; quem decide
 * a posse é o service/rota conforme o entitlement.
 */
@Serializable
data class ProgramDto(
    val id: String? = null,               // servidor preenche
    val name: String,                     // auto-gerado na criação; editável via PUT /programs/{id} (ARCH #27)
    val origin: WorkoutOrigin = WorkoutOrigin.AI,
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

/** Body de PUT /programs/{id} — renomear (único campo editável fora do motor). */
@Serializable
data class RenameProgramRequest(val name: String)

/** Body de POST /programs — cria programa manual vazio (sem motor) pra abrigar treino avulso. */
@Serializable
data class CreateManualProgramRequest(val name: String)

/**
 * Body de PUT /programs/{id}/schedule — reordena os treinos do programa.
 * `order` é a lista dos workoutIds na ordem desejada; o servidor grava day_of_week = posição+1
 * (1..N). Precisa ser uma permutação exata dos treinos do programa. Reorder de programa IA
 * exige premium (mesma porta de edição, ARCH #25) — evita furar o blur do value-first (#23).
 */
@Serializable
data class ReorderScheduleRequest(val order: List<String>)