package dev.rafael.features.program.presentation.state

sealed interface ProgramDetailEvent {
    data object Retry : ProgramDetailEvent
    data class Rename(val name: String) : ProgramDetailEvent
    data object Delete : ProgramDetailEvent
}
