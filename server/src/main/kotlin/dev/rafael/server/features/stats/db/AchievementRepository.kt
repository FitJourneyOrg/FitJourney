package dev.rafael.server.features.stats.db

import dev.rafael.core.result.AppResult
import kotlinx.datetime.LocalDateTime
import kotlin.uuid.Uuid

/**
 * Persistência das conquistas desbloqueadas (ARCH #16).
 *
 * Só existem duas operações, e não há remoção de propósito: conquista concedida nunca é
 * retirada, nem quando o progresso regride (streak quebra o tempo todo) nem quando a regra
 * muda. Não expor um `delete` é a forma mais barata de garantir isso.
 */
interface AchievementRepository {
    /** O que este usuário já tem, com a data original do desbloqueio. */
    suspend fun listByUser(userId: Uuid): AppResult<Map<String, LocalDateTime>>

    /**
     * Concede em lote. IDEMPOTENTE: id já concedido não duplica nem tem a data sobrescrita
     * (PK composta + ON CONFLICT DO NOTHING). Preservar a data original importa — ela é o
     * fato histórico que a tela ordena e que a notificação usa para saber o que é novo.
     */
    suspend fun grant(userId: Uuid, achievementIds: Set<String>): AppResult<Unit>
}
