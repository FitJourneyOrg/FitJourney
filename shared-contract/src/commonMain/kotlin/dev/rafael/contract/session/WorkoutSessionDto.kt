package dev.rafael.contract.session

import kotlinx.serialization.Serializable

/**
 * Uma série executada (log). Snapshot da prescrição (targetReps) + o que foi feito.
 * weightKg nullable (peso corporal / máquina sem carga).
 */
@Serializable
data class SetLogDto(
    val exerciseId: String,
    val orderIndex: Int,
    val setIndex: Int,
    val targetReps: Int,
    val repsDone: Int,
    val weightKg: Double? = null,
    val done: Boolean,
)

/**
 * Sessão de treino executada (Fase 5 — execução). Auto-contida (snapshot): sobrevive à
 * edição/exclusão do template. `id` é gerado no CLIENTE → o POST é idempotente (ON CONFLICT
 * DO NOTHING), o que deixa o sync offline seguro (reenvio não duplica). `startedAt`/`finishedAt`
 * vêm do relógio do cliente (o treino pode ter sido offline); o servidor grava seu created_at.
 */
@Serializable
data class WorkoutSessionDto(
    val id: String,                    // UUID gerado no cliente (idempotência)
    val programId: String? = null,
    val workoutId: String? = null,
    val workoutName: String,
    val startedAt: String,             // ISO LocalDateTime (relógio do cliente)
    val finishedAt: String,
    val sets: List<SetLogDto> = emptyList(),
)
