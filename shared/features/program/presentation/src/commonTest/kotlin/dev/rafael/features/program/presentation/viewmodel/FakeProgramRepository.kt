package dev.rafael.features.program.presentation.viewmodel

import dev.rafael.core.result.AppResult
import dev.rafael.features.program.domain.model.Program
import dev.rafael.features.program.domain.repository.ProgramRepository

/** Programa de domínio mínimo pros testes (workouts/schedule vazios não importam aqui). */
fun program(id: String, name: String = "Programa") = Program(
    id = id, name = name, workouts = emptyList(), daysPerWeek = 3,
    split = "Full Body", rationale = "r", locked = false,
    schedule = emptyList(), createdAt = null, updatedAt = null,
)

/** Fake configurável do repositório — cada teste ajusta só o resultado que exercita. */
class FakeProgramRepository(
    var listResult: AppResult<List<Program>> = AppResult.Success(emptyList()),
    var generateResult: AppResult<Program> = AppResult.Success(program("gen")),
    var createResult: AppResult<Program> = AppResult.Success(program("man")),
    var renameResult: AppResult<Program> = AppResult.Success(program("p1")),
    var deleteResult: AppResult<Unit> = AppResult.Success(Unit),
) : ProgramRepository {
    override suspend fun list() = listResult
    override suspend fun generate() = generateResult
    override suspend fun createManual(name: String) = createResult
    override suspend fun rename(id: String, name: String) = renameResult
    override suspend fun delete(id: String) = deleteResult
}
