package dev.rafael.server.features.user.db

import dev.rafael.core.result.AppResult
import dev.rafael.server.features.user.models.User
import kotlin.uuid.Uuid

/** Acesso a dados de usuário. Interface no server (não há domain/data separados no backend). */
interface UserRepository {
    suspend fun findByFirebaseUid(firebaseUid: String): AppResult<User?>
    suspend fun create(firebaseUid: String, email: String?): AppResult<User>
    /** Liga/desliga o premium do usuário. Retorna o user atualizado, ou null se não existe. */
    suspend fun setPremium(userId: Uuid, premium: Boolean): AppResult<User?>
}