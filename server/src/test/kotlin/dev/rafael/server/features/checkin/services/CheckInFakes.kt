package dev.rafael.server.features.checkin.services

import dev.rafael.contract.checkin.CheckInStatus
import dev.rafael.core.result.AppResult
import dev.rafael.core.result.asSuccess
import dev.rafael.server.features.checkin.db.CheckInRepository
import dev.rafael.server.features.checkin.db.FotoExpirada
import dev.rafael.server.features.checkin.models.CheckIn
import dev.rafael.server.features.checkin.models.CheckInComAutor
import dev.rafael.server.features.checkin.models.NovoCheckIn
import dev.rafael.server.media.ArmazenamentoDeMidia
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * Repositório de check-in em memória, com o **índice único de verdade**.
 *
 * A chave do mapa é `(grupo, usuário, dia)` — a mesma do `UNIQUE` no Postgres. Sem isso o teste da
 * corrida não provaria nada: um dublê que sempre aceita faria o "um por dia" passar por
 * construção.
 */
class FakeCheckInRepository : CheckInRepository {

    private val porDia = mutableMapOf<Triple<Uuid, Uuid, String>, NovoCheckIn>()

    /** Roda ANTES de gravar — é como se encena o toque duplo com a rede lenta. */
    var antesDeCriar: (() -> Unit)? = null

    val guardados: List<NovoCheckIn> get() = porDia.values.toList()

    fun semear(novo: NovoCheckIn) {
        porDia[chave(novo)] = novo
    }

    override suspend fun criar(novo: NovoCheckIn): AppResult<Boolean> {
        antesDeCriar?.invoke()
        val chave = chave(novo)
        if (chave in porDia) return false.asSuccess()   // o índice recusou
        porDia[chave] = novo
        return true.asSuccess()
    }

    override suspend fun porId(id: Uuid): AppResult<CheckInComAutor?> =
        porDia.values.firstOrNull { it.id == id }?.let(::comAutor).asSuccess()

    override suspend fun doDia(groupId: Uuid, userId: Uuid, dia: LocalDate): AppResult<Uuid?> =
        porDia[Triple(groupId, userId, dia.toString())]?.id.asSuccess()

    override suspend fun doGrupo(
        groupId: Uuid,
        limite: Int,
        antesDe: LocalDateTime?,
    ): AppResult<List<CheckInComAutor>> = porDia.values
        .filter { it.groupId == groupId && (antesDe == null || it.createdAt < antesDe) }
        .sortedByDescending { it.createdAt }        // 8.0.4: mais recente primeiro
        .take(limite)
        .map(::comAutor)
        .asSuccess()

    override suspend fun apagar(id: Uuid): AppResult<Unit> {
        porDia.entries.removeAll { it.value.id == id }
        return Unit.asSuccess()
    }

    // ---- purga: não exercitada por esta suíte; ver PurgaDeMidiaTest ----

    override suspend fun comFotoExpirada(carenciaEmDias: Int, limite: Int) =
        emptyList<FotoExpirada>().asSuccess()

    override suspend fun marcarPurgados(ids: List<Uuid>, agora: LocalDateTime) = Unit.asSuccess()

    override suspend fun refsVivas(): AppResult<Set<String>> =
        porDia.values.mapNotNull { it.photoRef }.toSet().asSuccess()

    /** Nomes de quem fez, para o feed. Sem isto o dublê não teria como preencher o `displayName`. */
    val nomes = mutableMapOf<Uuid, String>()

    /** Status por check-in — só a fatia E os muda; aqui existe para testar a guarda do apagar. */
    val status = mutableMapOf<Uuid, CheckInStatus>()

    private fun comAutor(n: NovoCheckIn) = CheckInComAutor(
        checkIn = CheckIn(
            id = n.id,
            groupId = n.groupId,
            userId = n.userId,
            localDate = n.localDate,
            createdAt = n.createdAt,
            status = status[n.id] ?: CheckInStatus.VALIDO,
            photoRef = n.photoRef,
            photoPurgedAt = null,
            placeName = n.placeName,
        ),
        displayName = nomes[n.userId] ?: "Alguém",
    )

    private fun chave(n: NovoCheckIn) = Triple(n.groupId, n.userId, n.localDate.toString())
}

/** Armazenamento em memória que CONTA — é assim que se prova que a foto órfã foi apagada. */
class FakeArmazenamento : ArmazenamentoDeMidia {

    private val arquivos = mutableMapOf<String, ByteArray>()
    var gravacoes = 0
        private set

    val refsVivas: Set<String> get() = arquivos.keys.toSet()

    override suspend fun guardar(bytes: ByteArray, extensao: String): AppResult<String> {
        gravacoes++
        val ref = "aa/bb/${"%032x".format(gravacoes)}.$extensao"
        arquivos[ref] = bytes
        return ref.asSuccess()
    }

    override suspend fun ler(ref: String): AppResult<ByteArray?> = arquivos[ref].asSuccess()

    override suspend fun apagar(ref: String): AppResult<Unit> {
        arquivos.remove(ref)
        return Unit.asSuccess()
    }

    /**
     * Este dublê não guarda a idade dos arquivos, então devolve tudo.
     *
     * Serve para esta suíte, que nunca roda a purga — quem exercita o recolhimento de órfãos é o
     * `PurgaDeMidiaTest`, com um dublê próprio que registra o instante de cada gravação. É lá que
     * o corte de 24h precisa ser afirmado, e não aqui.
     */
    override suspend fun listarRefs(anteriorA: Instant): AppResult<List<String>> =
        arquivos.keys.toList().asSuccess()
}
