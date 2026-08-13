package dev.rafael.features.program.data

import dev.rafael.contract.program.ScheduleEntry
import dev.rafael.core.network.TokenProvider
import dev.rafael.core.network.httpResult
import dev.rafael.core.result.AppError
import dev.rafael.core.result.AppResult
import dev.rafael.core.result.asFailure
import dev.rafael.core.result.asSuccess
import dev.rafael.features.program.domain.model.Program
import dev.rafael.features.program.domain.model.ProgramScheduleEntry
import dev.rafael.features.program.domain.repository.ProgramRepository
import kotlin.time.Clock

class ProgramRepositoryImpl(
    private val remote: ProgramDataSource,
    private val local: ProgramLocalDataSource,
    private val tokenProvider: TokenProvider,
) : ProgramRepository {

    // Momento do último sync bem-sucedido (singleton no Koin, então sobrevive à navegação).
    // null = cache sujo/inexistente -> próxima list() vai à rede.
    private var sincronizadoEm: Long? = null

    // Dono do cache em memória. Trocar de conta invalida: sem isto, o usuário novo veria
    // a lista do anterior como se estivesse fresca.
    private var donoDoCache: String? = null

    private val cacheFresco: Boolean
        get() = sincronizadoEm?.let { Clock.System.now().toEpochMilliseconds() - it < TTL_MS } == true

    /**
     * CACHE-FIRST. Antes isto era network-first e refazia GET /programs a cada entrada na aba.
     * Agora a rede só entra quando o cache está sujo, vencido ou vazio.
     *
     * O fallback offline continua: se a rede falhar por conexão (Unexpected), lê o cache mesmo
     * vencido. Erro do servidor (401/403/…) NÃO cai no cache — é resposta real, não falta de rede.
     */
    override suspend fun list(): AppResult<List<Program>> {
        val dono = tokenProvider.currentUid()
        if (dono != donoDoCache) invalidate()   // trocou de conta → nada de reaproveitar
        if (cacheFresco) {
            local.read()?.let { return it.map { dto -> dto.toDomain() }.asSuccess() }
        }
        return refresh()
    }

    override suspend fun refresh(): AppResult<List<Program>> =
        when (val net = httpResult { remote.list() }) {
            is AppResult.Success -> {
                local.save(net.value)
                sincronizadoEm = Clock.System.now().toEpochMilliseconds()
                donoDoCache = tokenProvider.currentUid()
                net.value.map { it.toDomain() }.asSuccess()
            }
            is AppResult.Failure -> {
                val cached = if (net.error is AppError.Unexpected) local.read() else null
                cached?.map { it.toDomain() }?.asSuccess() ?: net.error.asFailure()
            }
        }

    override fun invalidate() {
        sincronizadoEm = null
    }

    // --- mutações: invalidam o próprio cache (a próxima list() busca o estado novo) ---

    override suspend fun generate(): AppResult<Program> =
        httpResult { remote.generate().toDomain() }.also { invalidate() }

    override suspend fun createManual(name: String): AppResult<Program> =
        httpResult { remote.createManual(name).toDomain() }.also { invalidate() }

    override suspend fun rename(id: String, name: String): AppResult<Program> =
        httpResult { remote.rename(id, name).toDomain() }.also { invalidate() }

    override suspend fun delete(id: String): AppResult<Unit> =
        httpResult { remote.delete(id) }.also { invalidate() }

    override suspend fun setSchedule(id: String, schedule: List<ProgramScheduleEntry>): AppResult<Program> =
        httpResult {
            val entries = schedule.map { ScheduleEntry(workoutId = it.workoutId, dayOfWeek = it.dayOfWeek) }
            remote.setSchedule(id, entries).toDomain()
        }.also { invalidate() }

    private companion object {
        /** Janela em que o cache é considerado fresco. Trocar de aba nesse intervalo não vai à rede. */
        const val TTL_MS = 5 * 60 * 1000L
    }
}
