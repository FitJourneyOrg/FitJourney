package dev.rafael.server.features.profile.db

import dev.rafael.contract.profile.BodyLimitation
import dev.rafael.contract.profile.HealthScreening
import dev.rafael.contract.profile.MuscleGroup
import kotlinx.serialization.json.Json

private val json = Json

/** Lista de músculos <-> JSON string (coluna focus_areas TEXT). */
fun List<MuscleGroup>.toJson(): String = json.encodeToString(this)

fun String.toMuscleGroups(): List<MuscleGroup> =
    if (isBlank()) emptyList() else json.decodeFromString(this)


fun HealthScreening.toJson(): String = json.encodeToString(this)
fun String.toHealthScreening(): HealthScreening? =
    if (isBlank()) null else json.decodeFromString(this)



fun List<BodyLimitation>.limitationsToJson(): String = json.encodeToString(this)

fun String.toLimitations(): List<BodyLimitation> =
    if (isBlank()) emptyList() else json.decodeFromString(this)