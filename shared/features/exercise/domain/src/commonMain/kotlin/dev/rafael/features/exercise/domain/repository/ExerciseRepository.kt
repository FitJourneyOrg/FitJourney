package dev.rafael.features.exercise.domain.repository

import dev.rafael.contract.exercise.ExerciseCategory
import dev.rafael.core.result.AppResult
import dev.rafael.features.exercise.domain.model.Exercise
import kotlinx.coroutines.flow.Flow

interface ExerciseRepository {
    fun observeExercises(category: ExerciseCategory?): Flow<List<Exercise>>
    /**
     * Sincroniza o catálogo local. Respeita janela de frescor (o catálogo é semiestático,
     * vem de migration no servidor). `forcar = true` só quando o USUÁRIO pede (pull-to-refresh).
     */
    suspend fun refresh(forcar: Boolean = false): AppResult<Unit>
    /** Alternativas de mesmo tipo pra troca (GET /exercises/{id}/alternatives). */
    suspend fun alternatives(exerciseId: String): AppResult<List<Exercise>>
    /** Detalhe completo (com taxonomia) — via rede, pois o cache local não guarda a taxonomia. */
    suspend fun getDetail(exerciseId: String): AppResult<Exercise>
}