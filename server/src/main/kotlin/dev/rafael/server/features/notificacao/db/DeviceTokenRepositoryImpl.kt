package dev.rafael.server.features.notificacao.db

import dev.rafael.core.result.AppError
import dev.rafael.core.result.AppResult
import dev.rafael.core.result.asFailure
import dev.rafael.core.result.asSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDateTime
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.datetime.datetime
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.upsert
import kotlin.uuid.Uuid

/** Espelha `device_tokens` da V41. O TOKEN é a chave — ele já é único por instalação. */
object DeviceTokensTable : Table("device_tokens") {
    val token = text("token")
    val userId = uuid("user_id")
    val createdAt = datetime("created_at")
    val updatedAt = datetime("updated_at")

    override val primaryKey = PrimaryKey(token)
}

class DeviceTokenRepositoryImpl : DeviceTokenRepository {

    override suspend fun registrar(
        token: String,
        userId: Uuid,
        quando: LocalDateTime,
    ): AppResult<Unit> = dbQuery {
        // `upsert` = INSERT ... ON CONFLICT (token) DO UPDATE.
        //
        // Troca de dono é caso NORMAL: o mesmo aparelho pode receber login de outra conta. E
        // `createdAt` NÃO é atualizado de propósito — ele registra quando o APARELHO apareceu
        // pela primeira vez, e sobrescrevê-lo apagaria essa informação a cada re-registro.
        DeviceTokensTable.upsert(
            keys = arrayOf(DeviceTokensTable.token),
            onUpdate = {
                it[DeviceTokensTable.userId] = userId
                it[DeviceTokensTable.updatedAt] = quando
            },
        ) {
            it[DeviceTokensTable.token] = token
            it[DeviceTokensTable.userId] = userId
            it[DeviceTokensTable.createdAt] = quando
            it[DeviceTokensTable.updatedAt] = quando
        }
        Unit
    }

    override suspend fun doUsuario(userId: Uuid): AppResult<List<String>> = dbQuery {
        DeviceTokensTable.selectAll()
            .where { DeviceTokensTable.userId eq userId }
            .map { it[DeviceTokensTable.token] }
    }

    override suspend fun apagar(tokens: List<String>): AppResult<Unit> = dbQuery {
        // Lista vazia viraria `DELETE ... WHERE token IN ()`, que é SQL inválido em alguns bancos
        // e varredura completa em outros. Sair cedo é mais barato que confiar no dialeto.
        if (tokens.isNotEmpty()) {
            DeviceTokensTable.deleteWhere { DeviceTokensTable.token inList tokens }
        }
        Unit
    }

    private suspend fun <T> dbQuery(block: () -> T): AppResult<T> =
        withContext(Dispatchers.IO) {
            runCatching { transaction { block() } }.fold(
                onSuccess = { it.asSuccess() },
                onFailure = { AppError.Unexpected("Erro de banco", it).asFailure() },
            )
        }
}
