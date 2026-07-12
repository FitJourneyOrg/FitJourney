package dev.rafael.features.workout.presentation.state


data class WorkoutGenerateState(
    val prompt: String = "",
    val isGenerating: Boolean = false,
    val generatedId: String? = null,      // sinaliza navegação ao gerar
    val error: GenerateError? = null,
)

sealed interface GenerateError {
    data object Entitlement : GenerateError    // → paywall
    data object HealthGate : GenerateError     // → PAR-Q
    data class Other(val message: String) : GenerateError
}