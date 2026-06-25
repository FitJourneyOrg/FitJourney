package dev.rafael.contract.profile

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class Level {
    @SerialName("BEGINNER") BEGINNER,
    @SerialName("INTERMEDIATE") INTERMEDIATE,
    @SerialName("ADVANCED") ADVANCED,
}