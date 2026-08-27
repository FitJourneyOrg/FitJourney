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
import org.jetbrains.exposed.v1.jdbc.update
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

    override suspend fun findById(userId: Uuid): AppResult<User?> =
        dbQuery {
            UsersTable.selectAll()
                .where { UsersTable.id eq userId }
                .singleOrNull()
                ?.toUser()
        }

    override suspend fun create(
        id: Uuid,
        firebaseUid: String,
        email: String?,
        displayName: String,
    ): AppResult<User> =
        dbQuery {
            UsersTable.insert {
                it[UsersTable.id] = id
                it[UsersTable.firebaseUid] = firebaseUid
                it[UsersTable.email] = email
                it[UsersTable.isPremium] = false
                it[UsersTable.displayName] = displayName
            }
            User(
                id = id,
                firebaseUid = firebaseUid,
                email = email,
                isPremium = false,
                displayName = displayName,
            )
        }

    override suspend fun setPremium(userId: Uuid, premium: Boolean): AppResult<User?> =
        dbQuery {
            val n = UsersTable.update({ UsersTable.id eq userId }) { it[isPremium] = premium }
            if (n == 0) null
            else UsersTable.selectAll().where { UsersTable.id eq userId }.single().toUser()
        }

    override suspend fun updateDisplayName(userId: Uuid, displayName: String): AppResult<User?> =
        dbQuery {
            val n = UsersTable.update({ UsersTable.id eq userId }) {
                it[UsersTable.displayName] = displayName
            }
            if (n == 0) null
            else UsersTable.selectAll().where { UsersTable.id eq userId }.single().toUser()
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
    isPremium = this[UsersTable.isPremium],
    displayName = this[UsersTable.displayName],
)