package dev.rafael.server.features.exercise.db

import dev.rafael.contract.profile.Level
import dev.rafael.core.result.AppError
import dev.rafael.core.result.AppResult
import dev.rafael.core.result.asFailure
import dev.rafael.core.result.asSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.upsert
import kotlin.time.Clock
import kotlin.uuid.Uuid

private fun agora() = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())

/** `[REGRA]` espelha a CHECK da V70 — `BEGINNER` não é destino de promoção. */
private fun exigirNivelPromovivel(nivel: Level) =
    require(nivel == Level.INTERMEDIATE || nivel == Level.ADVANCED) {
        "nivelProposto deve ser INTERMEDIATE ou ADVANCED, recebeu $nivel"
    }

class PromotionDeclineRepositoryImpl : PromotionDeclineRepository {

    override suspend fun mesocicloDaUltimaRecusa(usuarioId: Uuid, nivelProposto: Level): AppResult<Int?> = dbQuery {
        exigirNivelPromovivel(nivelProposto)
        transaction {
            PromotionDeclineTable.selectAll()
                .where {
                    (PromotionDeclineTable.userId eq usuarioId) and
                        (PromotionDeclineTable.nivelProposto eq nivelProposto.name)
                }
                .singleOrNull()?.get(PromotionDeclineTable.mesocicloNaRecusa)
        }
    }

    override suspend fun registrarRecusa(usuarioId: Uuid, nivelProposto: Level, mesocicloAtual: Int): AppResult<Unit> = dbQuery {
        exigirNivelPromovivel(nivelProposto)
        require(mesocicloAtual >= 1) { "mesocicloAtual deve ser >= 1" }
        transaction {
            PromotionDeclineTable.upsert {
                it[userId] = usuarioId
                it[PromotionDeclineTable.nivelProposto] = nivelProposto.name
                it[mesocicloNaRecusa] = mesocicloAtual
                it[recusadoEm] = agora()
            }
        }
        Unit
    }
}

private suspend fun <T> dbQuery(block: () -> T): AppResult<T> =
    withContext(Dispatchers.IO) {
        runCatching { block() }.fold(
            onSuccess = { it.asSuccess() },
            onFailure = { AppError.Unexpected("Erro de banco", it).asFailure() },
        )
    }
