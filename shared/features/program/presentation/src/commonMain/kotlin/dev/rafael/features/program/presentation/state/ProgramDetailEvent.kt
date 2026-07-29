package dev.rafael.features.program.presentation.state

sealed interface ProgramDetailEvent {
    data object Retry : ProgramDetailEvent
    data class Rename(val name: String) : ProgramDetailEvent
    data object Delete : ProgramDetailEvent
    /** Reordenar (G.2): move o treino um passo p/ cima (up=true) ou baixo. */
    data class MoveWorkout(val workoutId: String, val up: Boolean) : ProgramDetailEvent
}
