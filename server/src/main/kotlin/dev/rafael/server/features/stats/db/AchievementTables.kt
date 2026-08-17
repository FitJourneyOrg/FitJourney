package dev.rafael.server.features.stats.db

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.datetime

/** Espelha V34__create_user_achievements.sql. */
object UserAchievementsTable : Table("user_achievements") {
    val userId = uuid("user_id")

    /**
     * TEXT, não enum do banco: conquista nova não deve exigir migration. O significado do id é
     * contrato (ver `AchievementPolicy.Conquista`) e nunca muda — renomear um id reescreveria
     * a história de quem já o tem.
     */
    val achievementId = text("achievement_id")

    /** Relógio do SERVIDOR ([REGRA]: gamificação nunca usa o relógio do cliente). */
    val unlockedAt = datetime("unlocked_at")

    /** Composta: é ela que torna a concessão idempotente sem código extra. */
    override val primaryKey = PrimaryKey(userId, achievementId)
}
