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
    var scheduleResult: AppResult<Program> = AppResult.Success(program("p1")),
) : ProgramRepository {
    var invalidateCalls = 0

    /**
     * BANCO LOCAL simulado (ARCH #30). Deliberadamente separado de `listResult` (a rede):
     * é justamente o descasamento entre os dois — tenho dado local mas a rede caiu, ou não
     * tenho nada e a rede caiu — que os testes de offline precisam exercitar.
     */
    val local = kotlinx.coroutines.flow.MutableStateFlow<List<Program>>(emptyList())

    override fun observePrograms(): kotlinx.coroutines.flow.Flow<List<Program>> = local

    /** Fila do outbox simulada (ARCH #30, B.4) — o teste empurra pendências e vê o selo. */
    val pendentes =
        kotlinx.coroutines.flow.MutableStateFlow<Set<dev.rafael.features.program.domain.model.PendenciaDeSync>>(emptySet())

    override fun observarPendentes() = pendentes

    /** Simula "já baixou neste aparelho, com esta conta" (carimbo persistido). */
    var sincronizouNesteAparelho: Boolean = false

    override suspend fun jaSincronizou() = sincronizouNesteAparelho
    override suspend fun list() = listResult
    override suspend fun refresh() = listResult
    override fun invalidate() { invalidateCalls++ }
    override suspend fun generate() = generateResult
    override suspend fun createManual(name: String) = createResult
    override suspend fun rename(id: String, name: String) = renameResult
    override suspend fun delete(id: String) = deleteResult
    override suspend fun setSchedule(id: String, schedule: List<dev.rafael.features.program.domain.model.ProgramScheduleEntry>) = scheduleResult
}
