package dev.rafael.contract.profile

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class MuscleGroup {
    @SerialName("CHEST") CHEST,
    @SerialName("BACK") BACK,
    @SerialName("ARMS") ARMS,
    @SerialName("SHOULDERS") SHOULDERS,
    @SerialName("LEGS") LEGS,
    @SerialName("GLUTES") GLUTES,
    @SerialName("CORE") CORE,
}