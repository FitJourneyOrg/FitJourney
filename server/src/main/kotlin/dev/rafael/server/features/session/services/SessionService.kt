package dev.rafael.server.features.session.services

import dev.rafael.contract.session.WorkoutSessionDto
import dev.rafael.core.result.AppError
import dev.rafael.core.result.AppResult
import dev.rafael.core.result.asFailure
import dev.rafael.core.result.flatMap
import dev.rafael.core.result.map
import dev.rafael.server.features.session.db.SessionRepository
import dev.rafael.server.features.session.models.toDomain
import dev.rafael.server.features.session.models.toDto
import dev.rafael.server.features.user.services.UserService

class SessionService(
    private val userService: UserService,
    private val repository: SessionRepository,
) {
    /**
     * Registra uma sessão executada (idempotente por id — ver repo). O userId vem do token,
     * não do corpo. Valida o mínimo; o snapshot em si é responsabilidade do cliente.
     */
    suspend fun record(firebaseUid: String, email: String?, dto: WorkoutSessionDto): AppResult<WorkoutSessionDto> {
        if (dto.sets.isEmpty()) {
            return AppError.Validation("A sessão precisa de ao menos 1 série").asFailure()
        }
        return userService.findOrCreate(firebaseUid, email).flatMap { user ->
            val session = runCatching { dto.toDomain(user.id) }.getOrNull()
                ?: return@flatMap AppError.Validation("Dados da sessão inválidos").asFailure()
            if (session.finishedAt < session.startedAt) {
                return@flatMap AppError.Validation("O fim do treino não pode ser antes do início").asFailure()
            }
            repository.save(session).map { dto }
        }
    }

    /** Histórico do usuário (mais recente primeiro). */
    suspend fun list(firebaseUid: String, email: String?): AppResult<List<WorkoutSessionDto>> =
        userService.findOrCreate(firebaseUid, email).flatMap { user ->
            repository.listByUser(user.id).map { list -> list.map { it.toDto() } }
        }
}
