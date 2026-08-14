package dev.rafael.features.workout.presentation.state

import dev.rafael.core.result.AppError

data class WorkoutFormState(
    val workoutId: String? = null,
    val programId: String? = null,   // obrigatório na criação (ARCH #27); ignorado na edição
    val name: String = "",
    val selectedDay: Int? = null,   // dia da semana (1=Seg..7=Dom); escolhido só na criação
    val takenDays: Set<Int> = emptySet(),   // dias já ocupados no programa (desabilitados no seletor)
    val exercises: List<FormExercise> = emptyList(),
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val error: AppError? = null,
    val savedId: String? = null,
) {
    val isEditing: Boolean get() = workoutId != null

    // espelha validate() do WorkoutService — programId só é exigido ao criar
    val canSave: Boolean
        get() = !isSaving &&
                (isEditing || programId != null) &&
                (isEditing || selectedDay != null) &&   // na criação, o dia é obrigatório
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
    // Preservados no round-trip (não editáveis na UI v1). Sem isso, editar um treino de IA
    // pelo form zeraria a prescrição do motor (#26). Exercício novo nasce com defaults.
    val restSeconds: Int = 90,
    val rir: Int? = null,
)