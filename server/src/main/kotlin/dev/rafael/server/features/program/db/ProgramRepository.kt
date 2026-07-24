package dev.rafael.server.features.program.db

import dev.rafael.core.result.AppResult
import dev.rafael.server.features.program.models.Program
import dev.rafael.server.features.program.models.ProgramCounts
import kotlin.uuid.Uuid

interface ProgramRepository {
    /** Contagem por origem (AI/MANUAL) — insumo dos gates de teto (ARCH #26). */
    suspend fun counts(userId: Uuid): AppResult<ProgramCounts>

    /** Insere um programa novo pro usuário. NÃO substitui os existentes (ARCH #26). */
    suspend fun createForUser(userId: Uuid, program: Program): AppResult<Program>

    /** Todos os programas do usuário, mais recente primeiro. */
    suspend fun findAllByUser(userId: Uuid): AppResult<List<Program>>

    /** Um programa específico do usuário (valida posse), ou null se não existe/não é dele. */
    suspend fun findByIdForUser(userId: Uuid, programId: Uuid): AppResult<Program?>

    /** Renomeia — único campo editável fora do motor. NotFound se não existe/não é do usuário. */
    suspend fun rename(userId: Uuid, programId: Uuid, name: String): AppResult<Program?>

    /** Remove o programa (CASCADE apaga os workouts). false se não existe/não é do usuário. */
    suspend fun delete(userId: Uuid, programId: Uuid): AppResult<Boolean>
}
