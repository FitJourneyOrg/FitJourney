package dev.rafael.features.workout.presentation.state

data class WorkoutDetailState(
    val id: String? = null,
    val name: String = "",
    val exercises: List<ResolvedExercise> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val isDeleted: Boolean = false,      // sinaliza pra tela navegar de volta
)

data class ResolvedExercise(
    val exerciseId: String,
    val name: String,                    // do catálogo; fallback se ausente
    val thumbRef: String?,               // null se não resolveu (ou sem mídia ainda)
    val setsSummary: String,             // ex.: "3 séries · 12/10/8 reps"
    val orderIndex: Int,
)