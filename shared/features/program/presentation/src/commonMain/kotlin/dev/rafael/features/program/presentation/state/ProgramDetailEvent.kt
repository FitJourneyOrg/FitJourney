package dev.rafael.features.program.presentation.state

sealed interface ProgramDetailEvent {
    data object Retry : ProgramDetailEvent
    data class Rename(val name: String) : ProgramDetailEvent
    data object Delete : ProgramDetailEvent
    /** Agenda (G.2): define o dia da semana (1=Seg..7=Dom) de um treino. */
    data class SetWorkoutDay(val workoutId: String, val dayOfWeek: Int) : ProgramDetailEvent
}
