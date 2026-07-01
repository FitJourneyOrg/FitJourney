package dev.rafael.contract.exercise

import kotlinx.serialization.Serializable

/**
 * Exercício do catálogo (read-only pro usuário). Fonte: catálogo de 963.
 * videoRef/thumbRef são referências relativas; o cliente monta a URL final com a base do CDN.
 */
@Serializable
data class ExerciseDto(
    val id: String,
    val name: String,
    val category: ExerciseCategory,
    val description: String? = null,   // HTML (tratado na exibição)
    val videoRef: String,              // ex.: "Trapézio/Remada Inclinada a 45 Graus.mp4"
    val thumbRef: String,              // ex.: "Trapézio/Remada Inclinada a 45 Graus.png"
)
