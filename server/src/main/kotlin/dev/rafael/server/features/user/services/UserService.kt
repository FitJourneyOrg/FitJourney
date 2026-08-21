package dev.rafael.server.features.user.services

import dev.rafael.core.result.AppError
import dev.rafael.core.result.AppResult
import dev.rafael.core.result.asFailure
import dev.rafael.core.result.asSuccess
import dev.rafael.core.result.flatMap
import dev.rafael.server.features.user.db.UserRepository
import dev.rafael.server.features.user.models.User
import kotlin.uuid.Uuid

class UserService(private val repository: UserRepository) {

    /**
     * Garante o usuário no Postgres a partir da identidade do Firebase.
     * 1o acesso: cria. Demais: retorna o existente. (find-or-create)
     */
    suspend fun findOrCreate(firebaseUid: String, email: String?): AppResult<User> {
        return when (val found = repository.findByFirebaseUid(firebaseUid)) {
            is AppResult.Failure -> found
            is AppResult.Success -> {
                val existing = found.value
                if (existing != null) {
                    AppResult.Success(existing)
                } else {
                    // Não existe -> cria. Corrida (2 requests do mesmo uid novo): o UNIQUE
                    // em firebase_uid faz o 2o insert falhar -> cai em Unexpected. Tratamento
                    // robusto (reler no conflito) fica como refino se a corrida aparecer.
                    //
                    // O NOME nasce aqui, não no onboarding (V35, #33). Este método roda no
                    // `GET /me` do splash, ANTES do quiz: se o nome esperasse o fim do
                    // onboarding, haveria uma janela com a linha criada e a coluna NOT NULL
                    // sem valor. O onboarding confirma/edita; ninguém fica sem nome.
                    val id = Uuid.random()
                    repository.create(
                        id = id,
                        firebaseUid = firebaseUid,
                        email = email,
                        displayName = DisplayNamePolicy.inicial(email, id),
                    )
                }
            }
        }
    }

    /**
     * Ativa o premium do usuário (compra simulada — Fase 7 dev). O passo de compra REAL
     * (Play Billing + verificação) fica no cliente atrás da interface Billing; aqui só liga
     * o flag. Idempotente. Depois, a verificação server-side de recibo entra na frente disto.
     */
    suspend fun activatePremium(firebaseUid: String, email: String?): AppResult<User> =
        findOrCreate(firebaseUid, email).flatMap { user ->
            repository.setPremium(user.id, true).flatMap { updated ->
                updated?.asSuccess() ?: AppError.NotFound("Usuário não encontrado").asFailure()
            }
        }

    /**
     * Renomeia o usuário (`PATCH /me`) — onboarding e tela de perfil usam o MESMO caminho.
     *
     * A validação vem PRIMEIRO, antes de tocar no banco: nome inválido é recusa, e recusa não
     * deve custar uma consulta. [REGRA] quem decide validade é o servidor, não a UI.
     */
    suspend fun updateDisplayName(
        firebaseUid: String,
        email: String?,
        displayName: String,
    ): AppResult<User> =
        DisplayNamePolicy.normalizar(displayName).flatMap { nome ->
            findOrCreate(firebaseUid, email).flatMap { user ->
                repository.updateDisplayName(user.id, nome).flatMap { updated ->
                    updated?.asSuccess() ?: AppError.NotFound("Usuário não encontrado").asFailure()
                }
            }
        }
}