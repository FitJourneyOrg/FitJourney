package dev.rafael.contract.profile

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class Goal {
    @SerialName("GAIN_MUSCLE") GAIN_MUSCLE,
    @SerialName("LOSE_FAT") LOSE_FAT,
    @SerialName("MAINTAIN") MAINTAIN,
    @SerialName("GENERAL_HEALTH") GENERAL_HEALTH,
}