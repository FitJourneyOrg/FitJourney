package dev.rafael.features.program.data

import dev.rafael.contract.error.ErrorResponse
import dev.rafael.core.result.AppError
import dev.rafael.core.result.AppResult
import dev.rafael.core.result.asFailure
import dev.rafael.core.result.asSuccess
import dev.rafael.features.program.domain.model.Program
import dev.rafael.features.program.domain.repository.ProgramRepository
import io.ktor.client.call.body
import io.ktor.client.plugins.ClientRequestException
import io.ktor.http.HttpStatusCode

class ProgramRepositoryImpl(
    private val remote: ProgramDataSource,
) : ProgramRepository {

    override suspend fun list(): AppResult<List<Program>> =
        call { remote.list().map { it.toDomain() } }

    override suspend fun generate(): AppResult<Program> =
        call { remote.generate().toDomain() }

    override suspend fun createManual(name: String): AppResult<Program> =
        call { remote.createManual(name).toDomain() }

    override suspend fun rename(id: String, name: String): AppResult<Program> =
        call { remote.rename(id, name).toDomain() }

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

                    else -> AppError.Unexpected("Falha na operação de programa", e).asFailure()
                }
            },
        )

    private suspend fun ClientRequestException.validationMessage(): String =
        runCatching { response.body<ErrorResponse>().message }
            .getOrElse { "Dados inválidos" }
}
