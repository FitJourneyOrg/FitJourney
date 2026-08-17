package dev.rafael.features.program.data

import dev.rafael.contract.program.ProgramDto
import dev.rafael.contract.program.RenameProgramRequest
import dev.rafael.contract.program.ScheduleEntry
import dev.rafael.contract.program.SetScheduleRequest
import dev.rafael.contract.workout.WorkoutOrigin
import dev.rafael.core.database.SyncStamps
import dev.rafael.core.database.outbox.AgendadorDeSync
import dev.rafael.core.database.outbox.Outbox
import dev.rafael.core.database.outbox.ProcessadorDeOutbox
import dev.rafael.core.database.outbox.TipoOperacao
import dev.rafael.core.network.httpResult
import dev.rafael.core.result.AppError
import dev.rafael.core.result.AppResult
import dev.rafael.core.result.asFailure
import dev.rafael.core.result.asSuccess
import dev.rafael.features.program.domain.model.PendenciaDeSync
import dev.rafael.features.program.domain.model.Program
import dev.rafael.features.program.domain.model.ProgramScheduleEntry
import dev.rafael.features.program.domain.repository.ProgramRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class ProgramRepositoryImpl(
    private val remote: ProgramDataSource,
    private val local: ProgramLocalDataSource,
    private val stamps: SyncStamps,
    private val outbox: Outbox,
    private val agendador: AgendadorDeSync,
    /**
     * Caminho de fundo #2 do outbox. Ver `enviarPendentes()`: o WorkManager sozinho não basta
     * quando o sistema decide não acordá-lo.
     */
    private val processador: ProcessadorDeOutbox,
    /**
     * Só para `updatedAt`, que ordena a lista na tela. [REGRA] o relógio do cliente NÃO decide
     * lógica — o servidor reescreve este campo no próximo sync.
     */
    private val clock: Clock,
) : ProgramRepository {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

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
        // `contarPendentes` entra na condição porque `sujo` é EM MEMÓRIA: fechar e reabrir o
        // app o zera, e com o carimbo ainda fresco a fila ficaria parada até o TTL vencer.
        // Era o segundo jeito de o selo "Pendente" grudar na tela.
        if (!sujo && outbox.contarPendentes() == 0L && stamps.fresco(SyncStamps.PROGRAMAS, TTL_MS)) {
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

    /**
     * ENVIAR ANTES DE BAIXAR.
     *
     * O KDoc do `Outbox` sempre prometeu que WorkManager e fila eram redundantes — "se o worker
     * atrasar, a próxima abertura do app esvazia". A promessa não tinha implementação: só o
     * worker chamava o processador. Resultado observado no emulador: rede volta, o WorkManager
     * não dispara, e o selo "Pendente" fica para sempre — trocar de tela não adianta, porque
     * nada em foreground processa.
     *
     * Aqui é o lugar certo, e não um listener de conectividade: baixar a verdade do servidor
     * ANTES de mandar o que eu tenho é a ordem errada de qualquer forma — o `save()` teria que
     * proteger pendências que já poderiam ter subido.
     *
     * Falha em silêncio de propósito: sem rede, `processar()` devolve TENTAR_DEPOIS e o
     * `refresh()` seguinte falha sozinho com a mensagem certa.
     */
    private suspend fun enviarPendentes() {
        runCatching { processador.processar() }
    }

    override suspend fun refresh(): AppResult<List<Program>> {
        enviarPendentes()
        return when (val net = httpResult { remote.list() }) {
            is AppResult.Success -> {
                // Preserva o que ainda está na fila: o servidor não conhece o que foi criado
                // offline, e sobrescrever cegamente faria o dado sumir da tela do usuário.
                local.save(net.value, pendentes = outbox.alvosPendentes())
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
    }

    override fun invalidate() {
        sujo = true
    }

    override fun observarPendentes(): Flow<Set<PendenciaDeSync>> =
        outbox.observar().map { fila ->
            // A fila guarda o HISTÓRICO bruto (3 edições = 3 linhas); a tela só quer saber
            // quais ALVOS estão pendentes. Um alvo com qualquer falha permanente é mostrado
            // como falha — é a informação que exige ação do usuário.
            fila.groupBy { it.alvoId }.map { (alvo, itens) ->
                PendenciaDeSync(
                    alvoId = alvo,
                    erroPermanente = itens.firstNotNullOfOrNull { it.erroPermanente },
                )
            }.toSet()
        }

    // --- mutações ---

    /**
     * ÚNICA mutação que continua ONLINE-ONLY, de propósito.
     *
     * Gerar programa é trabalho do motor no servidor ([REGRA] autoridade do servidor): não há
     * resultado otimista para mostrar — o cliente não sabe que treinos a IA vai montar.
     * Enfileirar isto só adiaria a mesma espera e daria ao usuário um programa vazio no lugar.
     */
    override suspend fun generate(): AppResult<Program> =
        httpResult { remote.generate().toDomain() }.also { invalidate() }

    /**
     * Criação otimista com id do CLIENTE (B.1 fez o servidor aceitá-lo), o que torna o POST
     * idempotente: resposta perdida na rede → reenvio com o mesmo id → nenhum programa duplicado.
     */
    override suspend fun createManual(name: String): AppResult<Program> {
        val id = Uuid.random().toString()
        val agora = clock.now().toString()
        val dto = ProgramDto(
            id = id,
            name = name,
            origin = WorkoutOrigin.MANUAL,
            daysPerWeek = 0,
            split = "",
            rationale = "",
            createdAt = agora,
            updatedAt = agora,
        )
        local.criarPrograma(dto)
        enfileirar(TipoOperacao.CRIAR_PROGRAMA, id, json.encodeToString(ProgramDto.serializer(), dto))
        return dto.toDomain().asSuccess()
    }

    override suspend fun rename(id: String, name: String): AppResult<Program> {
        if (ehDeIa(id)) return httpResult { remote.rename(id, name).toDomain() }.also { invalidate() }
        local.renomearPrograma(id, name, clock.now().toString())
        enfileirar(
            TipoOperacao.RENOMEAR_PROGRAMA, id,
            json.encodeToString(RenameProgramRequest.serializer(), RenameProgramRequest(name)),
        )
        return devolverLocal(id)
    }

    override suspend fun delete(id: String): AppResult<Unit> {
        if (ehDeIa(id)) return httpResult { remote.delete(id) }.also { invalidate() }
        local.excluirPrograma(id)
        enfileirar(TipoOperacao.EXCLUIR_PROGRAMA, id)
        return Unit.asSuccess()
    }

    override suspend fun setSchedule(id: String, schedule: List<ProgramScheduleEntry>): AppResult<Program> {
        val entries = schedule.map { ScheduleEntry(workoutId = it.workoutId, dayOfWeek = it.dayOfWeek) }
        // Agendar programa de IA passa pela mesma porta de edição (ARCH #25) — ver ehDeIa.
        if (ehDeIa(id)) return httpResult { remote.setSchedule(id, entries).toDomain() }
            .also { invalidate() }
        local.definirAgenda(id, entries)
        enfileirar(
            TipoOperacao.DEFINIR_AGENDA, id,
            json.encodeToString(SetScheduleRequest.serializer(), SetScheduleRequest(entries)),
        )
        return devolverLocal(id)
    }

    /**
     * Ver `WorkoutRepositoryImpl.ehDeIa`: o outbox cobre o que o usuário POSSUI. Programa de IA
     * é travado por entitlement (ARCH #25) e a recusa tem de chegar no momento da ação, não
     * minutos depois pela fila.
     */
    private suspend fun ehDeIa(id: String): Boolean =
        local.lerPrograma(id)?.origin == WorkoutOrigin.AI

    private suspend fun enfileirar(tipo: TipoOperacao, id: String, payload: String = "") {
        invalidate()
        outbox.enfileirar(tipo, id, payload)
        agendador.agendar()
    }

    /**
     * A UI espera o programa de volta depois da mutação. Como a fonte da verdade local acabou
     * de ser gravada, é dela que lemos — não da rede. `NotFound` aqui é bug de programação
     * (mutar um id que não existe localmente), não erro esperado do usuário.
     */
    private suspend fun devolverLocal(id: String): AppResult<Program> =
        local.lerPrograma(id)?.toDomain()?.asSuccess()
            ?: AppError.NotFound("Programa não encontrado").asFailure()

    private companion object {
        /** Janela em que o cache é considerado fresco. Trocar de aba nesse intervalo não vai à rede. */
        const val TTL_MS = 5 * 60 * 1000L
    }
}
