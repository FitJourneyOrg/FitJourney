package dev.rafael.features.program.presentation.state

sealed interface ProgramListEvent {
    data object Load : ProgramListEvent
    data object Retry : ProgramListEvent
    data class CreateManual(val name: String) : ProgramListEvent
    /** Usuário reconheceu uma falha permanente do outbox e quer descartar (ARCH #30). */
    data class Descartar(val alvoId: String) : ProgramListEvent
}
