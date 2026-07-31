package dev.rafael.features.program.data

import dev.rafael.contract.program.ScheduleEntry
import dev.rafael.core.network.httpResult
import dev.rafael.core.result.AppResult
import dev.rafael.features.program.domain.model.Program
import dev.rafael.features.program.domain.model.ProgramScheduleEntry
import dev.rafael.features.program.domain.repository.ProgramRepository

class ProgramRepositoryImpl(
    private val remote: ProgramDataSource,
) : ProgramRepository {

    override suspend fun list(): AppResult<List<Program>> =
        httpResult { remote.list().map { it.toDomain() } }

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
