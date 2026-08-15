package dev.rafael.features.program.data

import dev.rafael.contract.program.ScheduleEntry
import dev.rafael.core.database.SyncStamps
import dev.rafael.core.network.httpResult
import dev.rafael.core.result.AppError
import dev.rafael.core.result.AppResult
import dev.rafael.core.result.asFailure
import dev.rafael.core.result.asSuccess
import dev.rafael.features.program.domain.model.Program
import dev.rafael.features.program.domain.model.ProgramScheduleEntry
import dev.rafael.features.program.domain.repository.ProgramRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ProgramRepositoryImpl(
    private val remote: ProgramDataSource,
    private val local: ProgramLocalDataSource,
    private val stamps: SyncStamps,
) : ProgramRepository {

    /**
     * "Mutei algo AGORA, nesta sessão." Fica em memória de propósito: `invalidate()` é chamado
     * de lambdas de UI (não-suspend) no NavHost, e gravar no banco exigiria tornar a interface
     * suspend e mudar todos os call sites.
     *
     * Divisão de trabalho: o carimbo PERSISTIDO responde "está fresco entre aberturas do app";
     * este flag responde "eu mesmo acabei de mudar". Cache sujo é a soma dos dois.
     */
    private var sujo = false

    /**
     * CACHE-FIRST. Antes isto era network-first e refazia GET /programs a cada entrada na aba.
     * Agora a rede só entra quando o cache está sujo, vencido ou vazio.
     *
     * O fallback offline continua: se a rede falhar por conexão (Unexpected), lê o cache mesmo
     * vencido. Erro do servidor (401/403/…) NÃO cai no cache — é resposta real, não falta de rede.
     */
    override fun observePrograms(): Flow<List<Program>> =
        local.observar().map { dtos -> dtos.map { it.toDomain() } }

    override suspend fun list(): AppResult<List<Program>> {
        // Trocar de conta não precisa mais de checagem manual: o carimbo é chaveado por uid,
        // então a conta nova simplesmente não encontra o da anterior. Era aqui que o
        // isolamento dependia de alguém lembrar de comparar `donoDoCache`.
        if (!sujo && stamps.fresco(SyncStamps.PROGRAMAS, TTL_MS)) {
            // Lista vazia é resposta VÁLIDA: "sincronizei e você não tem programas". Antes o
            // read() devolvia null nesse caso e caía na rede em toda abertura.
            return local.read().map { dto -> dto.toDomain() }.asSuccess()
        }
        return refresh()
    }

    /**
     * Já baixou programas neste aparelho, com esta conta?
     *
     * A UI usa isto para distinguir "você não tem programas" de "ainda não baixei" — sem esta
     * resposta, uma conta que já sincronizou ontem e abre o app offline hoje via "Sem conexão"
     * como se nunca tivesse baixado nada.
     */
    override suspend fun jaSincronizou(): Boolean = stamps.jaSincronizou(SyncStamps.PROGRAMAS)

    override suspend fun refresh(): AppResult<List<Program>> =
        when (val net = httpResult { remote.list() }) {
            is AppResult.Success -> {
                local.save(net.value)
                stamps.marcar(SyncStamps.PROGRAMAS)
                sujo = false
                net.value.map { it.toDomain() }.asSuccess()
            }
            is AppResult.Failure -> {
                // Servir cache exige DUAS condições:
                //  1. falha de TRANSPORTE — o servidor não disse nada, então o último dado
                //     conhecido segue válido. 401/403/404/500 são resposta real e propagam.
                //  2. já ter sincronizado alguma vez nesta conta — senão "lista vazia" seria
                //     indistinguível de "nunca baixei", e a tela diria "você não tem programas"
                //     a quem só está offline num aparelho novo.
                val podeServirCache = net.error is AppError.Connection &&
                    stamps.jaSincronizou(SyncStamps.PROGRAMAS)
                if (podeServirCache) local.read().map { it.toDomain() }.asSuccess()
                else net.error.asFailure()
            }
        }

    override fun invalidate() {
        sujo = true
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
