package dev.rafael.features.program.presentation.state

sealed interface ProgramGenerateEvent {
    data object Generate : ProgramGenerateEvent
    data object DismissError : ProgramGenerateEvent
}
