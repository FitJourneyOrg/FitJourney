package dev.rafael.features.program.presentation.state

sealed interface ProgramListEvent {
    data object Load : ProgramListEvent
    data object Retry : ProgramListEvent
    data class CreateManual(val name: String) : ProgramListEvent

    /** V59 -- marca este treino como o ativo do usuário. */
    data class Activate(val workoutId: String) : ProgramListEvent
}
