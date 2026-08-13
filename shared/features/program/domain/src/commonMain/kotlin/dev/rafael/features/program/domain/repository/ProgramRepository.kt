package dev.rafael.features.program.domain.repository

import dev.rafael.core.result.AppResult
import dev.rafael.features.program.domain.model.Program
import dev.rafael.features.program.domain.model.ProgramScheduleEntry

interface ProgramRepository {
    /**
     * CACHE-FIRST: devolve o cache local se ainda estiver fresco; só vai à rede quando o cache
     * está vazio, vencido (TTL) ou foi invalidado por uma mutação. Trocar de aba não refaz
     * GET /programs a cada vez.
     */
    suspend fun list(): AppResult<List<Program>>                  // GET /programs (condicional)

    /** Força ida à rede (pull-to-refresh / "tentar de novo"), ignorando o cache. */
    suspend fun refresh(): AppResult<List<Program>>

    /**
     * Marca o cache como sujo — a próxima `list()` vai à rede. Chamar depois de qualquer
     * mudança feita FORA desta feature (ex.: criar/editar/excluir treino, virar premium),
     * já que as mutações daqui invalidam sozinhas.
     */
    fun invalidate()

    suspend fun generate(): AppResult<Program>                     // POST /programs/generate
    suspend fun createManual(name: String): AppResult<Program>     // POST /programs
    suspend fun rename(id: String, name: String): AppResult<Program>  // PUT /programs/{id}
    suspend fun delete(id: String): AppResult<Unit>                    // DELETE /programs/{id}
    suspend fun setSchedule(id: String, schedule: List<ProgramScheduleEntry>): AppResult<Program>  // PUT /programs/{id}/schedule
}
