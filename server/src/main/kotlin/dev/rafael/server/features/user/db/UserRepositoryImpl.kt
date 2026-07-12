package dev.rafael.server.features.user.db

import dev.rafael.core.result.AppError
import dev.rafael.core.result.AppResult
import dev.rafael.core.result.asFailure
import dev.rafael.core.result.asSuccess
import dev.rafael.server.features.user.models.User
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.uuid.Uuid

class UserRepositoryImpl : UserRepository {

    override suspend fun findByFirebaseUid(firebaseUid: String): AppResult<User?> =
        dbQuery {
            UsersTable.selectAll()
                .where { UsersTable.firebaseUid eq firebaseUid }
                .singleOrNull()
                ?.toUser()
        }

    override suspend fun create(firebaseUid: String, email: String?): AppResult<User> =
        dbQuery {
            val newId = Uuid.random()
            UsersTable.insert {
                it[UsersTable.id] = newId
                it[UsersTable.firebaseUid] = firebaseUid
                it[UsersTable.email] = email
                it[UsersTable.isPremium] = false
            }
            User(id = newId, firebaseUid = firebaseUid, email = email, false)
        }

    /** Exposed é bloqueante -> IO. Qualquer exceção do banco vira AppError.Unexpected (não vaza). */
    private suspend fun <T> dbQuery(block: () -> T): AppResult<T> =
        withContext(Dispatchers.IO) {
            runCatching { transaction { block() } }.fold(
                onSuccess = { it.asSuccess() },
                onFailure = { AppError.Unexpected("Erro de banco", it).asFailure() },
            )
        }
}

private fun ResultRow.toUser(): User = User(
    id = this[UsersTable.id],
    firebaseUid = this[UsersTable.firebaseUid],
    email = this[UsersTable.email],
    isPremium = this[UsersTable.isPremium],   // <- novo
)