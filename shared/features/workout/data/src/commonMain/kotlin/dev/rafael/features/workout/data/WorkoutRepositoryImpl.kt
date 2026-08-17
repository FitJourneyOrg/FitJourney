package dev.rafael.features.workout.data

import dev.rafael.contract.workout.WorkoutDto
import dev.rafael.contract.workout.WorkoutOrigin
import dev.rafael.core.database.SyncStamps
import dev.rafael.core.database.outbox.AgendadorDeSync
import dev.rafael.core.database.outbox.Outbox
import dev.rafael.core.database.outbox.TipoOperacao
import dev.rafael.core.network.httpResult
import dev.rafael.core.result.AppError
import dev.rafael.core.result.AppResult
import dev.rafael.core.result.asFailure
import dev.rafael.core.result.asSuccess
import dev.rafael.features.workout.domain.model.Workout
import dev.rafael.features.workout.domain.model.WorkoutSummary
import dev.rafael.features.workout.domain.repository.WorkoutRepository
import kotlinx.serialization.json.Json
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * ESCRITA VIA OUTBOX (ARCH #30, fatia B.3).
 *
 * Um caminho só, com ou sem rede: grava local → enfileira → pede o envio. A alternativa
 * ("se tem rede manda direto, senão enfileira") produz dois comportamentos, dois conjuntos de
 * bug, e deixa o caminho offline exercitado apenas quando algo já deu errado.
 *
 * O efeito visível: salvar um treino é INSTANTÂNEO e nunca falha por rede. O erro, quando
 * existe, é do servidor e chega depois — pela fila, não pelo botão.
 */
@OptIn(ExperimentalUuidApi::class)
class WorkoutRepositoryImpl(
    private val remote: WorkoutDataSource,
    private val local: WorkoutLocalDataSource,
    private val stamps: SyncStamps,
    private val outbox: Outbox,
    private val agendador: AgendadorDeSync,
) : WorkoutRepository {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    override suspend fun list(): AppResult<List<WorkoutSummary>> =
        httpResult { remote.list().map { it.toDomain() } }

    /**
     * CACHE-FIRST ([REGRA] ARCH #30: a tela lê do banco, sync é de fundo).
     *
     * Antes isto era network-first — apesar do comentário dizer "offline-first", ia à rede em
     * TODA abertura e só usava o cache se a rede falhasse. O efeito no log do servidor: um
     * `GET /workouts/{id}` por navegação, mais um 403 a cada vez que a Home (viva no back
     * stack) reagia ao Flow e buscava o treino do dia trancado.
     */
    override suspend fun get(id: String): AppResult<Workout> {
        // PENDENTE => o local é a verdade. O servidor ou não conhece o recurso (404, porque a
        // fila ainda não subiu) ou tem a versão anterior — e as duas respostas apagariam o que
        // o usuário acabou de digitar. Sem esta guarda, abrir o treino recém-criado online,
        // antes do worker rodar, daria "não encontrado" em cima de um dado que está na tela.
        if (id in outbox.alvosPendentes()) {
            local.read(id)?.let { return it.toDomain().asSuccess() }
        }
        // Carimbo por treino E por uid (`sync:workout:{id}:{uid}`) — o isolamento vem da chave.
        if (stamps.fresco(SyncStamps.treino(id), TTL_MS)) {
            local.read(id)?.let { return it.toDomain().asSuccess() }
        }
        return refresh(id)
    }

    /** Vai à rede e regrava o local. Falha de TRANSPORTE cai no cache (p/ treinar offline). */
    private suspend fun refresh(id: String): AppResult<Workout> =
        when (val net = httpResult { remote.get(id) }) {
            is AppResult.Success -> {
                local.save(id, net.value)
                stamps.marcar(SyncStamps.treino(id))
                net.value.toDomain().asSuccess()
            }
            is AppResult.Failure -> {
                // Ver ProgramRepositoryImpl: só transporte cai no cache; resposta do servidor, não.
                val cached = if (net.error is AppError.Connection) local.read(id) else null
                cached?.toDomain()?.asSuccess() ?: net.error.asFailure()
            }
        }

    /** Muda o treino → o carimbo dele morre, e o próximo get() busca o estado novo. */
    private suspend fun invalidar(id: String) {
        stamps.invalidar(SyncStamps.treino(id))
    }

    /**
     * O id é GERADO AQUI, no cliente. É o que torna o reenvio seguro: se a resposta do POST se
     * perder na rede, a segunda tentativa manda o mesmo id e o servidor devolve o recurso que
     * já criou (B.1: `insertIgnore` + releitura) em vez de duplicar o treino.
     */
    override suspend fun create(workout: Workout): AppResult<Workout> {
        val id = workout.id?.takeIf { it.isNotBlank() } ?: Uuid.random().toString()
        val comId = workout.copy(id = id)
        gravarEEnfileirar(TipoOperacao.CRIAR_TREINO, id, comId.toDto())
        return comId.asSuccess()
    }

    override suspend fun update(id: String, workout: Workout): AppResult<Workout> {
        // ARCH #25: editar conteúdo de IA exige premium, e QUEM DECIDE ISSO É O SERVIDOR.
        if (ehDeIa(id)) return httpResult { remote.update(id, workout.toDto()).toDomain() }
            .also { invalidar(id) }

        val comId = workout.copy(id = id)
        gravarEEnfileirar(TipoOperacao.EDITAR_TREINO, id, comId.toDto())
        return comId.asSuccess()
    }

    override suspend fun delete(id: String): AppResult<Unit> {
        if (ehDeIa(id)) return httpResult { remote.delete(id) }.also { invalidar(id) }

        local.excluir(id)          // some da tela agora; o Flow do programa reage sozinho
        invalidar(id)
        outbox.enfileirar(TipoOperacao.EXCLUIR_TREINO, id)
        agendador.agendar()
        return Unit.asSuccess()
    }

    /**
     * O OUTBOX COBRE O QUE O USUÁRIO POSSUI.
     *
     * Conteúdo de origem IA é travado por entitlement (ARCH #25): a resposta 403
     * ENTITLEMENT_REQUIRED é o que abre o paywall no `WorkoutDetailViewModel`. Se essa
     * mutação virasse otimista, o usuário free veria "salvo", o paywall nunca apareceria, e
     * minutos depois a edição sumiria sozinha quando a fila levasse o 403 — regressão numa
     * rota de monetização, disfarçada de melhoria offline.
     *
     * Conteúdo MANUAL é do usuário: ninguém pode recusá-lo, então vale a escrita otimista.
     *
     * O preço: premium não edita treino de IA offline. Aceitável enquanto a autoridade sobre
     * entitlement for do servidor ([REGRA]) — a alternativa exigiria confiar na cópia local
     * de `isPremium`, que é otimista por definição.
     */
    private suspend fun ehDeIa(id: String): Boolean =
        local.read(id)?.origin == WorkoutOrigin.AI

    /** O trio que toda mutação faz, na ordem que importa. */
    private suspend fun gravarEEnfileirar(tipo: TipoOperacao, id: String, dto: WorkoutDto) {
        // 1. local primeiro: se o processo morrer entre as duas linhas, o pior caso é um dado
        //    salvo que não subiu — recuperável. O inverso (enfileirado sem estar local) mostraria
        //    a tela vazia e depois o dado reaparecendo do nada.
        local.save(id, dto)
        // A AGENDA junto, senão o treino existe no banco e não aparece na tela: a visão de
        // semana do programa monta os 7 dias a partir de `program_schedule` e mostra
        // "Descanso" onde não há entrada. Online quem faz isso é o servidor ao receber o POST.
        val programa = dto.programId
        val dia = dto.dayOfWeek
        if (programa != null && dia != null) local.agendar(programa, id, dia)
        invalidar(id)
        // 2. a intenção, com o corpo exato que será enviado
        outbox.enfileirar(tipo, id, json.encodeToString(WorkoutDto.serializer(), dto))
        // 3. acorda o envio (no-op se não houver rede — o WorkManager espera por ela)
        agendador.agendar()
    }

    private companion object {
        /** Mesma janela do ProgramRepositoryImpl — política de frescor única no app. */
        const val TTL_MS = 5 * 60 * 1000L
    }
}
