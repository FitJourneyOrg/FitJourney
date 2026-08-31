package dev.rafael.server.features.notificacao.db

import dev.rafael.core.result.AppResult
import dev.rafael.server.features.notificacao.models.Notificacao
import kotlinx.datetime.LocalDateTime
import kotlin.uuid.Uuid

interface NotificationRepository {

    /** Grava. É a VERDADE da notificação — o push é só o aviso dela. */
    suspend fun criar(n: Notificacao): AppResult<Unit>

    /** As minhas, mais recente primeiro. Teto para a lista não crescer sem limite na tela. */
    suspend fun doUsuario(userId: Uuid, limite: Int): AppResult<List<Notificacao>>

    /** Quantas não lidas. É o número do sininho. */
    suspend fun naoLidas(userId: Uuid): AppResult<Int>

    /**
     * Marca TODAS as minhas como lidas.
     *
     * Marcar uma a uma seria mais granular e pior: abrir a central é o gesto de "vi tudo isto", e
     * exigir um toque por item deixaria o contador aceso sobre coisas que a pessoa já leu.
     */
    suspend fun marcarTodasComoLidas(userId: Uuid, quando: LocalDateTime): AppResult<Int>

    /** Apaga o que passou da retenção (6 meses). Devolve quantas saíram, para o log. */
    suspend fun purgar(anterioresA: LocalDateTime): AppResult<Int>
}
