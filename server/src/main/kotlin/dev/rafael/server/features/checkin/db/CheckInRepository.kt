package dev.rafael.server.features.checkin.db

import dev.rafael.core.result.AppResult
import dev.rafael.server.features.checkin.models.CheckInComAutor
import dev.rafael.server.features.checkin.models.NovoCheckIn
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlin.uuid.Uuid

/** Acesso a dados de check-in (fatia B). */
interface CheckInRepository {

    /**
     * Grava. Devolve `false` quando **já existe** check-in seu neste grupo hoje (4.3).
     *
     * Quem decide isso é o índice único, não um `SELECT` antes — dois toques com a rede lenta são
     * duas requisições em voo, e só o banco resolve o empate. Diferente do `join` de grupo, aqui
     * o segundo pedido **não** é idempotente: ele traria outra foto, e a regra é uma por dia.
     */
    suspend fun criar(novo: NovoCheckIn): AppResult<Boolean>

    suspend fun porId(id: Uuid): AppResult<CheckInComAutor?>

    /** O meu check-in de um DIA específico neste grupo, ou `null`. Consulta o índice único. */
    suspend fun doDia(groupId: Uuid, userId: Uuid, dia: LocalDate): AppResult<Uuid?>

    /**
     * O FEED do grupo (8.0.4): mais recente primeiro.
     *
     * [antesDe] é cursor, e não número de página: o feed recebe item novo o tempo todo (polling de
     * 10s, 8.3), e paginar por deslocamento faria a página 2 repetir ou pular linhas conforme a
     * lista cresce por cima.
     */
    suspend fun doGrupo(groupId: Uuid, limite: Int, antesDe: LocalDateTime? = null): AppResult<List<CheckInComAutor>>

    /** Apaga de verdade — é o que LIBERA o slot do dia (4.11). */
    suspend fun apagar(id: Uuid): AppResult<Unit>
}
