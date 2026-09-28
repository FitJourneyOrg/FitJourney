package dev.rafael.features.program.presentation.state

sealed interface ProgramListEvent {
    data object Load : ProgramListEvent
    data object Retry : ProgramListEvent
    data class CreateManual(val name: String) : ProgramListEvent
    /** V60 (reverte a V59, volta pra cá): marca este programa como o ativo do usuário. */
    data class Activate(val programId: String) : ProgramListEvent
    /** Usuário reconheceu uma falha permanente do outbox e quer descartar (ARCH #30). */
    data class Descartar(val alvoId: String) : ProgramListEvent
}
