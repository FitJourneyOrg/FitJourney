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
import dev.rafael.contract.error.ErrorCodes

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
            return AppError.Validation("A sessão precisa de ao menos 1 série", code = ErrorCodes.SESSAO_SEM_SERIE).asFailure()
        }
        return userService.findOrCreate(firebaseUid, email).flatMap { user ->
            val session = runCatching { dto.toDomain(user.id) }.getOrNull()
                ?: return@flatMap AppError.Validation("Não consegui salvar este treino concluído. Ele continua na fila e vou tentar de novo.", code = ErrorCodes.SESSAO_INVALIDA).asFailure()
            if (session.finishedAt < session.startedAt) {
                return@flatMap AppError.Validation("O fim do treino não pode ser antes do início", code = ErrorCodes.SESSAO_COM_FIM_ANTES_DO_INICIO).asFailure()
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
