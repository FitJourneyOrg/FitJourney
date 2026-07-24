package dev.rafael.features.workout.data

import dev.rafael.contract.error.ErrorResponse
import dev.rafael.contract.workout.WorkoutDto
import dev.rafael.core.result.AppError
import dev.rafael.core.result.AppResult
import dev.rafael.core.result.asFailure
import dev.rafael.core.result.asSuccess
import dev.rafael.features.workout.domain.model.Workout
import dev.rafael.features.workout.domain.model.WorkoutSummary
import dev.rafael.features.workout.domain.repository.WorkoutRepository
import io.ktor.client.call.body
import io.ktor.client.plugins.ClientRequestException
import io.ktor.http.HttpStatusCode

class WorkoutRepositoryImpl(
    private val remote: WorkoutDataSource,
) : WorkoutRepository {

    override suspend fun list(): AppResult<List<WorkoutSummary>> =
        call { remote.list().map { it.toDomain() } }

    override suspend fun get(id: String): AppResult<Workout> =
        call { remote.get(id).toDomain() }

    override suspend fun create(workout: Workout): AppResult<Workout> =
        call { remote.create(workout.toDto()).toDomain() }

    override suspend fun update(id: String, workout: Workout): AppResult<Workout> =
        call { remote.update(id, workout.toDto()).toDomain() }

    override suspend fun delete(id: String): AppResult<Unit> =
        call { remote.delete(id) }

    private suspend fun <T> call(block: suspend () -> T): AppResult<T> =
        runCatching { block() }.fold(
            onSuccess = { it.asSuccess() },
            onFailure = { e ->
                when (e) {
                    is ClientRequestException if e.response.status == HttpStatusCode.Unauthorized ->
                        AppError.Unauthorized("Sessão expirada. Faça login novamente.").asFailure()

                    is ClientRequestException if e.response.status == HttpStatusCode.Forbidden -> {
                        val error = runCatching { e.response.body<ErrorResponse>() }.getOrNull()
                        AppError.Forbidden(
                            message = error?.message ?: "Sem permissão",
                            code = error?.code,
                        ).asFailure()
                    }
                    is ClientRequestException if e.response.status == HttpStatusCode.BadRequest ->
                        AppError.Validation(e.validationMessage()).asFailure()



                    else -> AppError.Unexpected("Falha na operação de treino", e).asFailure()
                }
            },
        )

    private suspend fun ClientRequestException.validationMessage(): String =
        runCatching { response.body<ErrorResponse>().message }
            .getOrElse { "Dados inválidos" }   // corpo ilegível → mensagem genérica
}