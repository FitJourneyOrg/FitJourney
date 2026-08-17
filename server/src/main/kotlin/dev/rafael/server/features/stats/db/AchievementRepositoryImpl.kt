package dev.rafael.server.features.stats.db

import dev.rafael.core.result.AppError
import dev.rafael.core.result.AppResult
import dev.rafael.core.result.asFailure
import dev.rafael.core.result.asSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.time.Clock
import kotlin.uuid.Uuid

class AchievementRepositoryImpl : AchievementRepository {

    override suspend fun listByUser(userId: Uuid): AppResult<Map<String, LocalDateTime>> = dbQuery {
        UserAchievementsTable.selectAll()
            .where { UserAchievementsTable.userId eq userId }
            .associate { it[UserAchievementsTable.achievementId] to it[UserAchievementsTable.unlockedAt] }
    }

    override suspend fun grant(userId: Uuid, achievementIds: Set<String>): AppResult<Unit> = dbQuery {
        if (achievementIds.isEmpty()) return@dbQuery   // caso comum: nada novo, nenhuma escrita

        // Um `now()` só para o lote inteiro: conquistas desbloqueadas pelo MESMO treino devem
        // ter a mesma data. Chamar o relógio por linha produziria milissegundos diferentes e a
        // tela ordenaria arbitrariamente algo que aconteceu junto.
        val agora = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())

        achievementIds.forEach { conquista ->
            // insertIgnore = ON CONFLICT DO NOTHING sobre a PK (user_id, achievement_id).
            // É o que garante a idempotência SEM ler antes: a concessão roda a cada sessão
            // registrada, e reconceder não pode duplicar nem sobrescrever a data original —
            // que é o fato histórico exibido na tela.
            UserAchievementsTable.insertIgnore {
                it[UserAchievementsTable.userId] = userId
                it[achievementId] = conquista
                it[unlockedAt] = agora
            }
        }
    }

    private suspend fun <T> dbQuery(block: () -> T): AppResult<T> =
        withContext(Dispatchers.IO) {
            runCatching { transaction { block() } }.fold(
                onSuccess = { it.asSuccess() },
                onFailure = { AppError.Unexpected("Erro de banco", it).asFailure() },
            )
        }
}
