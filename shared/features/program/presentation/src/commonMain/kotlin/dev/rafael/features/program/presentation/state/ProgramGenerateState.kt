package dev.rafael.features.program.presentation.state

data class ProgramGenerateState(
    val isGenerating: Boolean = false,
    val generatedId: String? = null,      // sinaliza navegação ao gerar
    val error: GenerateError? = null,
)

sealed interface GenerateError {
    data object Entitlement : GenerateError    // → paywall (teto de programas grátis, ARCH #26)
    data object HealthGate : GenerateError     // → PAR-Q
    data class Other(val message: String) : GenerateError
}
