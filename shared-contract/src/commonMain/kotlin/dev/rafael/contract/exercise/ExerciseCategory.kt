package dev.rafael.contract.exercise

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** As 16 categorias do catálogo de exercícios (taxonomia própria, distinta do MuscleGroup do quiz). */
@Serializable
enum class ExerciseCategory {
    @SerialName("CORE") CORE,
    @SerialName("FUNCTIONAL_HIT") FUNCTIONAL_HIT,
    @SerialName("MOBILITY") MOBILITY,
    @SerialName("CALISTHENICS") CALISTHENICS,
    @SerialName("LEGS") LEGS,
    @SerialName("SHOULDERS") SHOULDERS,
    @SerialName("CHEST") CHEST,
    @SerialName("BACK") BACK,
    @SerialName("CROSSFIT") CROSSFIT,
    @SerialName("BICEPS") BICEPS,
    @SerialName("TRICEPS") TRICEPS,
    @SerialName("TRAPEZIUS") TRAPEZIUS,
    @SerialName("GLUTES") GLUTES,
    @SerialName("CALVES") CALVES,
    @SerialName("FOREARMS") FOREARMS,
    @SerialName("CARDIO") CARDIO,
    @SerialName("LOWER_BACK") LOWER_BACK,
}