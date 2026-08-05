package dev.rafael.features.program.data

import dev.rafael.contract.program.ScheduleEntry
import dev.rafael.core.network.httpResult
import dev.rafael.core.result.AppError
import dev.rafael.core.result.AppResult
import dev.rafael.core.result.asFailure
import dev.rafael.core.result.asSuccess
import dev.rafael.features.program.domain.model.Program
import dev.rafael.features.program.domain.model.ProgramScheduleEntry
import dev.rafael.features.program.domain.repository.ProgramRepository

class ProgramRepositoryImpl(
    private val remote: ProgramDataSource,
    private val local: ProgramLocalDataSource,
) : ProgramRepository {

    // Offline-first: rede → cacheia + retorna; falha de REDE (Unexpected) → cai no cache local.
    // Erro do servidor (401/403/etc) NÃO cai no cache (está online, é resposta real).
    override suspend fun list(): AppResult<List<Program>> =
        when (val net = httpResult { remote.list() }) {
            is AppResult.Success -> {
                local.save(net.value)
                net.value.map { it.toDomain() }.asSuccess()
            }
            is AppResult.Failure -> {
                val cached = if (net.error is AppError.Unexpected) local.read() else null
                cached?.map { it.toDomain() }?.asSuccess() ?: net.error.asFailure()
            }
        }

    override suspend fun generate(): AppResult<Program> =
        httpResult { remote.generate().toDomain() }

    override suspend fun createManual(name: String): AppResult<Program> =
        httpResult { remote.createManual(name).toDomain() }

    override suspend fun rename(id: String, name: String): AppResult<Program> =
        httpResult { remote.rename(id, name).toDomain() }

    override suspend fun delete(id: String): AppResult<Unit> =
        httpResult { remote.delete(id) }

    override suspend fun setSchedule(id: String, schedule: List<ProgramScheduleEntry>): AppResult<Program> =
        httpResult {
            val entries = schedule.map { ScheduleEntry(workoutId = it.workoutId, dayOfWeek = it.dayOfWeek) }
            remote.setSchedule(id, entries).toDomain()
        }
}
