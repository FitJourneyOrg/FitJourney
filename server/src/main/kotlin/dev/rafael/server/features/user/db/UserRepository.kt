package dev.rafael.server.features.user.db

import dev.rafael.core.result.AppResult
import dev.rafael.server.features.user.models.User
import kotlin.uuid.Uuid

/** Acesso a dados de usuário. Interface no server (não há domain/data separados no backend). */
interface UserRepository {
    suspend fun findByFirebaseUid(firebaseUid: String): AppResult<User?>

    /**
     * O `id` e o `displayName` vêm de FORA (V35, #33). O repositório não inventa nem um nem
     * outro: id é decisão de domínio — a mesma escolha do outbox no cliente (#30), onde quem
     * gera é quem manda — e o nome é `DisplayNamePolicy`, que precisa do id para o fallback.
     */
    suspend fun create(id: Uuid, firebaseUid: String, email: String?, displayName: String): AppResult<User>

    /** Liga/desliga o premium do usuário. Retorna o user atualizado, ou null se não existe. */
    suspend fun setPremium(userId: Uuid, premium: Boolean): AppResult<User?>

    /** Renomeia. O nome já vem validado por `DisplayNamePolicy`. Null = usuário não existe. */
    suspend fun updateDisplayName(userId: Uuid, displayName: String): AppResult<User?>
}