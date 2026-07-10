package dev.rafael.features.workout.presentation.state

data class WorkoutFormState(
    val workoutId: String? = null,
    val name: String = "",
    val exercises: List<FormExercise> = emptyList(),
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val error: String? = null,
    val savedId: String? = null,
) {
    val isEditing: Boolean get() = workoutId != null

    // espelha validate() do WorkoutService
    val canSave: Boolean
        get() = !isSaving &&
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