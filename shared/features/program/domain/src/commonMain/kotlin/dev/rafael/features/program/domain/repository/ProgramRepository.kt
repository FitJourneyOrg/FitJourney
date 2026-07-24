package dev.rafael.features.program.domain.repository

import dev.rafael.core.result.AppResult
import dev.rafael.features.program.domain.model.Program

interface ProgramRepository {
    suspend fun list(): AppResult<List<Program>>                  // GET /programs
    suspend fun generate(): AppResult<Program>                     // POST /programs/generate
    suspend fun createManual(name: String): AppResult<Program>     // POST /programs
    suspend fun rename(id: String, name: String): AppResult<Program>  // PUT /programs/{id}
    suspend fun delete(id: String): AppResult<Unit>                    // DELETE /programs/{id}
}
