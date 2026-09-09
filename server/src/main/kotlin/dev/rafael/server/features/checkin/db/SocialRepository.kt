package dev.rafael.server.features.checkin.db

import dev.rafael.core.result.AppResult
import dev.rafael.server.features.checkin.models.Comentario
import dev.rafael.server.features.checkin.models.NovoComentario
import dev.rafael.server.features.checkin.models.ReacaoAgrupada
import kotlinx.datetime.LocalDateTime
import kotlin.uuid.Uuid

/**
 * Comentários e reações (fatia E.1).
 *
 * Separado do [CheckInRepository] porque são tabelas próprias com regras próprias — e porque o
 * `CheckInRepository` já tem nove operações. Um repositório que cresce sem parar vira o lugar onde
 * tudo cabe, e aí nada nele é encontrável.
 */
interface SocialRepository {

    // ---- comentários ----

    suspend fun comentar(novo: NovoComentario): AppResult<Comentario>

    /** Do mais antigo para o mais novo: conversa se lê na ordem em que aconteceu. */
    suspend fun comentarios(checkInId: Uuid): AppResult<List<Comentario>>

    suspend fun comentario(id: Uuid): AppResult<Comentario?>

    suspend fun apagarComentario(id: Uuid): AppResult<Unit>

    /**
     * Quantos comentários cada check-in tem, para o feed.
     *
     * **Em LOTE**, e não uma consulta por card: o feed traz até 20 check-ins, e um `count` por item
     * é o N+1 que o `seed_volume.sql` já revelou em `meusGrupos`. Código novo imita código
     * existente, e por isso este método nasce assim.
     */
    suspend fun contarComentarios(checkInIds: List<Uuid>): AppResult<Map<Uuid, Int>>

    // ---- reações ----

    /**
     * Põe ou TROCA a minha reação. Uma por pessoa (8.2) — a PK composta garante.
     *
     * `INSERT ... ON CONFLICT (check_in_id, user_id) DO UPDATE`: trocar não precisa consultar
     * antes. Quando a regra cabe numa chave, ela para de depender de código correto.
     */
    suspend fun reagir(
        checkInId: Uuid,
        groupId: Uuid,
        userId: Uuid,
        emoji: String,
        quando: LocalDateTime,
    ): AppResult<Unit>

    /** Tira a minha reação. Idempotente: tirar o que não existe não é erro. */
    suspend fun desreagir(checkInId: Uuid, userId: Uuid): AppResult<Unit>

    /**
     * As reações agrupadas por emoji, para uma lista de check-ins.
     *
     * Agrupado no BANCO, não no Kotlin: trazer 50 linhas por card para contar em memória seria
     * pagar rede e alocação por um número que o `GROUP BY` já dá.
     */
    suspend fun reacoes(checkInIds: List<Uuid>, doUsuario: Uuid): AppResult<Map<Uuid, List<ReacaoAgrupada>>>
}
