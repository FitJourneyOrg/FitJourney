package dev.rafael.contract.exercise

import dev.rafael.contract.profile.Level
import dev.rafael.contract.profile.MuscleGroup
import kotlinx.serialization.Serializable

/**
 * Exercício do catálogo (read-only pro usuário). Fonte: catálogo de 963.
 * videoRef/thumbRef são referências relativas; o cliente monta a URL final com a base do CDN.
 * description é prosa (não é HTML, apesar do nome antigo).
 *
 * Campos de taxonomia (músculos, equipamento, etc.) alimentam as seções da tela de detalhe.
 * Têm default pra retrocompat de desserialização (nem toda origem preenche).
 */
@Serializable
data class ExerciseDto(
    val id: String,
    val name: String,
    val category: ExerciseCategory,
    val description: String? = null,
    val videoRef: String,              // ex.: "Trapézio/Remada Inclinada a 45 Graus.mp4"
    val thumbRef: String,              // ex.: "Trapézio/Remada Inclinada a 45 Graus.png"
    // --- taxonomia (detalhe) ---
    val primaryMuscles: List<MuscleGroup> = emptyList(),
    val secondaryMuscles: List<MuscleGroup> = emptyList(),
    val equipment: String? = null,           // ex.: "BARBELL", "MACHINE", "BODYWEIGHT"
    val movementPattern: String? = null,     // ex.: "SQUAT", "HINGE", "NONE"
    val isCompound: Boolean? = null,         // composto vs isolamento
    val unilateral: Boolean? = null,         // uni vs bilateral
    val prescriptionType: String? = null,    // "REPS" | "TIME"
    val level: Level? = null,
)
