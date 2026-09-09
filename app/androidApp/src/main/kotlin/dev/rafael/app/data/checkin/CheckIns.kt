package dev.rafael.app.data.checkin

import dev.rafael.contract.checkin.CheckInDto
import dev.rafael.contract.checkin.CommentDto
import dev.rafael.contract.checkin.ReportItemDto
import dev.rafael.contract.group.RankingEntryDto
import dev.rafael.core.result.AppResult
import dev.rafael.core.result.map

/**
 * Check-in como as TELAS o enxergam (fatia B).
 *
 * **Tudo online-only, sem cache e fora do outbox** (10.1) — e aqui o motivo é mais forte que no
 * nome do usuário: o check-in depende do relógio e do fuso do SERVIDOR para saber em que dia cai,
 * e o "um por dia" é decidido por um índice único no banco. Um check-in otimista local seria um
 * registro que talvez o servidor recuse por já existir — e a pessoa veria o próprio check-in
 * desaparecer da tela depois de comemorar.
 *
 * O feed também não tem cache nesta fatia: ele muda por ação de OUTRAS pessoas o tempo todo, que
 * é exatamente o caso em que TTL é atraso e não economia (a mesma lição da lista de grupos).
 */
interface CheckIns {

    /**
     * Faz o check-in. A foto vai crua daqui: quem comprime é o [CompressorDeFoto], chamado pela
     * tela logo depois da captura, porque é lá que a rotação do CameraX é conhecida.
     */
    suspend fun fazer(
        groupId: String,
        foto: ByteArray?,
        nomeDoLocal: String?,
        latitude: Double?,
        longitude: Double?,
    ): AppResult<CheckInDto>

    suspend fun feed(groupId: String, antesDe: String? = null): AppResult<List<CheckInDto>>

    suspend fun apagar(groupId: String, checkInId: String): AppResult<Unit>

    /** O ranking do grupo (7.2). Posição e desempate vêm resolvidos do servidor. */
    suspend fun ranking(groupId: String): AppResult<List<RankingEntryDto>>

    // ---- social (fatia E.1) ----

    /**
     * Os comentários de um check-in. Só quando a pessoa ABRE — o feed traz apenas a contagem.
     *
     * Um card com 30 comentários viraria a tela inteira, e baixar todos para mostrar "3
     * comentários" seria pagar caro por um número.
     */
    suspend fun comentarios(groupId: String, checkInId: String): AppResult<List<CommentDto>>

    suspend fun comentar(groupId: String, checkInId: String, texto: String): AppResult<CommentDto>

    /** Apaga. O servidor decide se posso — o `canDelete` do DTO já disse à tela. */
    suspend fun apagarComentario(groupId: String, comentarioId: String): AppResult<Unit>

    /** Põe ou TROCA a minha reação (8.2). Uma por pessoa — a PK do banco garante. */
    suspend fun reagir(groupId: String, checkInId: String, emoji: String): AppResult<Unit>

    suspend fun desreagir(groupId: String, checkInId: String): AppResult<Unit>

    // ---- moderação (fatia E.2) ----

    /**
     * Denuncia. O motivo é **obrigatório** — o atrito é a regra, não um descuido de UX.
     *
     * O `canReport` do DTO já disse à tela se o botão aparece; isto é o pedido em si, e o servidor
     * confere de novo (prazo, autoria, alvo do grupo).
     */
    suspend fun denunciarCheckIn(groupId: String, checkInId: String, motivo: String): AppResult<Unit>

    suspend fun denunciarComentario(groupId: String, comentarioId: String, motivo: String): AppResult<Unit>

    /** A fila do admin (6.2). Membro comum recebe 403 — a tela nem é oferecida a ele. */
    suspend fun fila(groupId: String): AppResult<List<ReportItemDto>>

    /**
     * Quantos casos esperam, para o badge da toolbar.
     *
     * Chamada separada da [fila] de propósito: a barra precisa do número a cada abertura do
     * detalhe do grupo, e baixar a fila hidratada para exibir um "3" seria pagar a página por um
     * inteiro.
     */
    suspend fun pendentes(groupId: String): AppResult<Int>

    /** Julga. O id é o do ALVO — várias denúncias do mesmo conteúdo fecham juntas (6.11). */
    suspend fun julgar(groupId: String, alvoId: String, acatar: Boolean): AppResult<Unit>

    /** 6.10: o admin invalida direto, sem denúncia prévia. */
    suspend fun invalidar(groupId: String, checkInId: String): AppResult<Unit>
}

/** Sem cache: o repositório é uma casca fina sobre a API, e isso é decisão, não preguiça. */
class CheckInsRepository(private val api: CheckInsApi) : CheckIns {

    override suspend fun fazer(
        groupId: String,
        foto: ByteArray?,
        nomeDoLocal: String?,
        latitude: Double?,
        longitude: Double?,
    ): AppResult<CheckInDto> = api.criar(groupId, foto, nomeDoLocal, latitude, longitude)

    override suspend fun feed(groupId: String, antesDe: String?): AppResult<List<CheckInDto>> =
        api.feed(groupId, antesDe)

    override suspend fun apagar(groupId: String, checkInId: String): AppResult<Unit> =
        api.apagar(groupId, checkInId)

    override suspend fun ranking(groupId: String): AppResult<List<RankingEntryDto>> =
        api.ranking(groupId)

    override suspend fun comentarios(groupId: String, checkInId: String) =
        api.comentarios(groupId, checkInId)

    override suspend fun comentar(groupId: String, checkInId: String, texto: String) =
        api.comentar(groupId, checkInId, texto)

    override suspend fun apagarComentario(groupId: String, comentarioId: String) =
        api.apagarComentario(groupId, comentarioId)

    override suspend fun reagir(groupId: String, checkInId: String, emoji: String) =
        api.reagir(groupId, checkInId, emoji)

    override suspend fun desreagir(groupId: String, checkInId: String) =
        api.desreagir(groupId, checkInId)

    override suspend fun denunciarCheckIn(groupId: String, checkInId: String, motivo: String) =
        api.denunciarCheckIn(groupId, checkInId, motivo)

    override suspend fun denunciarComentario(groupId: String, comentarioId: String, motivo: String) =
        api.denunciarComentario(groupId, comentarioId, motivo)

    override suspend fun fila(groupId: String) = api.fila(groupId)

    /** Desembrulha o DTO do badge aqui: a tela quer um número, não um envelope. */
    override suspend fun pendentes(groupId: String) = api.pendentes(groupId).map { it.pending }

    override suspend fun julgar(groupId: String, alvoId: String, acatar: Boolean) =
        api.julgar(groupId, alvoId, acatar)

    override suspend fun invalidar(groupId: String, checkInId: String) =
        api.invalidar(groupId, checkInId)
}
