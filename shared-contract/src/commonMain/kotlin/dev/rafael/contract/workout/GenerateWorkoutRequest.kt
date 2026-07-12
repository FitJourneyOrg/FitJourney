package dev.rafael.contract.workout

import kotlinx.serialization.Serializable

/** Pedido de geração por IA. prompt é texto livre opcional; o resto vem do perfil no server. */
@Serializable
data class GenerateWorkoutRequest(
    val prompt: String? = null,
)