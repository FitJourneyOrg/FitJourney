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
                    //
                    // O CÓDIGO também nasce aqui (V40, #35), pelo mesmo motivo do nome: a
                    // coluna é NOT NULL, e gerá-lo "quando alguém precisar" exigiria que ela
                    // fosse nullable — o que a A.0 já pagou para evitar.
                    val id = Uuid.random()
                    repository.create(
                        id = id,
                        firebaseUid = firebaseUid,
                        email = email,
                        displayName = DisplayNamePolicy.inicial(email, id),
                        code = UserCodePolicy.gerar(),
                    )
                }
            }
        }
    }

    /**
     * Gera um código novo e mata o anterior (35.5).
     *
     * **Só uma tentativa de colisão, e ela vira erro.** Com 32⁸ ≈ 1 trilhão de códigos, colidir
     * é evento de loteria; um laço de retry aqui seria código que nunca roda e por isso nunca é
     * testado. Melhor falhar alto e a pessoa tocar de novo — o botão está na frente dela.
     */
    suspend fun regenerarCodigo(firebaseUid: String, email: String?): AppResult<User> =
        findOrCreate(firebaseUid, email).flatMap { user ->
            repository.updateCode(user.id, UserCodePolicy.gerar()).flatMap { atualizado ->
                atualizado?.asSuccess() ?: AppError.NotFound("Usuário não encontrado").asFailure()
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