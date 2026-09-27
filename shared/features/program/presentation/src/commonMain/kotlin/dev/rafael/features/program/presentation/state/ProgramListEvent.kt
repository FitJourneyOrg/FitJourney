package dev.rafael.features.program.presentation.state

sealed interface ProgramListEvent {
    data object Load : ProgramListEvent
    data object Retry : ProgramListEvent
    data class CreateManual(val name: String) : ProgramListEvent
}
