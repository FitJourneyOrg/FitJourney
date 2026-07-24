package dev.rafael.contract.workout

import kotlinx.serialization.Serializable

/**
 * Treino completo (aggregate). Usado no POST (criar), GET/{id} (ler), PUT (editar).
 * id/createdAt/updatedAt são nulos no POST (o servidor gera); preenchidos na resposta.
 *
 * programId (ARCH #26): obrigatório no POST — todo treino vive dentro de um programa
 * (não existe mais treino avulso solto). O servidor valida posse (o programa precisa
 * ser do usuário autenticado) e calcula dayOfWeek a partir da posição dentro do programa.
 */
@Serializable
data class WorkoutDto(
    val id: String? = null,
    val name: String,
    val origin: WorkoutOrigin = WorkoutOrigin.MANUAL,
    val programId: String? = null,   // obrigatório na criação (validado no service); vem preenchido na leitura
    val exercises: List<WorkoutExerciseDto> = emptyList(),
    val createdAt: String? = null,   // ISO-8601; servidor preenche
    val updatedAt: String? = null,
)