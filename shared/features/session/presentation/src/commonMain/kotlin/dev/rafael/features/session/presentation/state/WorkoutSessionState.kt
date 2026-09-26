package dev.rafael.features.session.presentation.state

import dev.rafael.core.result.AppError

/** Uma série editável na execução (reps/carga como texto p/ o input). */
data class SetEntry(
    val exerciseId: String,
    /** `null` quando o catálogo não conhece este id. Quem escreve a palavra é a TELA (G.3). */
    val exerciseName: String?,
    val orderIndex: Int,
    val setIndex: Int,
    val targetReps: Int,
    val repsDone: String,
    val weight: String,
    val done: Boolean,
    val restSeconds: Int,   // prescrito pelo motor (ARCH #26 §3.2)
)

data class WorkoutSessionState(
    val workoutName: String = "",
    val entries: List<SetEntry> = emptyList(),
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val error: AppError? = null,
    val saved: Boolean = false,
    // --- descanso ---
    val restRemaining: Int? = null,   // segundos restantes; null = sem descanso rodando
    val restTotal: Int = 0,           // duração prescrita (p/ a barra de progresso)
    val restDoneTick: Int = 0,        // incrementa quando um descanso zera (UI vibra)
) {
    val canFinish: Boolean get() = !isSaving && entries.any { it.done }
}

sealed interface SessionEvent {
    data class RepsChanged(val index: Int, val value: String) : SessionEvent
    data class WeightChanged(val index: Int, val value: String) : SessionEvent
    data class ToggleDone(val index: Int) : SessionEvent
    data object Finish : SessionEvent
    // descanso
    data object SkipRest : SessionEvent
    data class AddRest(val seconds: Int) : SessionEvent
}
