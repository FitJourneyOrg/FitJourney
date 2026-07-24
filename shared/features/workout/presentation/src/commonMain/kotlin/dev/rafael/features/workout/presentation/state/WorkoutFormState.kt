package dev.rafael.features.workout.presentation.state

data class WorkoutFormState(
    val workoutId: String? = null,
    val programId: String? = null,   // obrigatório na criação (ARCH #26); ignorado na edição
    val name: String = "",
    val exercises: List<FormExercise> = emptyList(),
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val error: String? = null,
    val savedId: String? = null,
) {
    val isEditing: Boolean get() = workoutId != null

    // espelha validate() do WorkoutService — programId só é exigido ao criar
    val canSave: Boolean
        get() = !isSaving &&
                (isEditing || programId != null) &&
                name.isNotBlank() &&
                exercises.isNotEmpty() &&
                exercises.all { ex ->
                    ex.sets.isNotEmpty() && ex.sets.all { (it.toIntOrNull() ?: 0) > 0 }
                }
}

data class FormExercise(
    val exerciseId: String,
    val name: String,
    val sets: List<String>,   // reps como texto — permite campo vazio durante a digitação
)