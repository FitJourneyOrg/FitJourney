package dev.rafael.contract.profile

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class MuscleGroup {
    @SerialName("CHEST") CHEST,
    @SerialName("BACK") BACK,
    @SerialName("BICEPS") BICEPS,
    @SerialName("TRICEPS") TRICEPS,
    @SerialName("FOREARMS") FOREARMS,
    @SerialName("SHOULDERS") SHOULDERS,
    @SerialName("LEGS") LEGS,
    @SerialName("GLUTES") GLUTES,
    @SerialName("CORE") CORE,
}