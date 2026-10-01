package dev.rafael.features.exercise.domain.repository

import dev.rafael.core.result.AppResult
import dev.rafael.features.exercise.domain.model.Exercise
import dev.rafael.features.exercise.domain.model.FiltroDeExercicios
import kotlinx.coroutines.flow.Flow

interface ExerciseRepository {
    /**
     * Os três eixos de [FiltroDeExercicios], aplicados em ordem de custo crescente.
     *
     * `categoria` filtra no SQL (coluna indexada). `musculo` e `busca` filtram em memória, sobre o
     * resultado: o cache guarda a taxonomia como JSON numa coluna TEXT (5.sqm), e SQLite sem a
     * extensão json1 não indexa nem consulta dentro dela; a busca precisa ignorar acento, que o
     * `LIKE` não faz.
     *
     * Os eixos COEXISTEM (decisão do Rafael): categoria é estilo de treino, músculo é anatomia —
     * uma pessoa pode querer "CROSSFIT" + "pernas" ao mesmo tempo, e ainda digitar "pistola".
     */
    fun observeExercises(filtro: FiltroDeExercicios = FiltroDeExercicios()): Flow<List<Exercise>>
    /**
     * Sincroniza o catálogo local. Respeita janela de frescor (o catálogo é semiestático,
     * vem de migration no servidor). `forcar = true` só quando o USUÁRIO pede (pull-to-refresh).
     */
    suspend fun refresh(forcar: Boolean = false): AppResult<Unit>
    /** Alternativas de mesmo tipo pra troca (GET /exercises/{id}/alternatives). */
    suspend fun alternatives(exerciseId: String): AppResult<List<Exercise>>
    /** Detalhe completo (com taxonomia) — via rede, pois o cache local não guarda a taxonomia inteira. */
    suspend fun getDetail(exerciseId: String): AppResult<Exercise>
}