package dev.rafael.features.workout.presentation.state

import dev.rafael.core.result.AppError

data class WorkoutDetailState(
    val id: String? = null,
    val name: String = "",
    val exercises: List<ResolvedExercise> = emptyList(),
    val isLoading: Boolean = false,
    val error: AppError? = null,
    val isDeleted: Boolean = false,      // sinaliza pra tela navegar de volta
    val showPaywall: Boolean = false,    // 403 ao editar programa IA sem premium
    /**
     * Alguma mutação foi de fato aplicada (trocar/remover exercício). A tela usa isto ao
     * voltar para decidir se invalida o cache de programas: antes invalidava SEMPRE, então
     * só entrar e sair de um treino já forçava um GET /programs na volta.
     */
    val alterado: Boolean = false,
)

data class ResolvedExercise(
    val exerciseId: String,
    val name: String,                    // do catálogo; fallback se ausente
    val thumbRef: String?,               // null se não resolveu (ou sem mídia ainda)
    val setsSummary: String,             // ex.: "3 séries · 12/10/8 reps"
    val orderIndex: Int,
)