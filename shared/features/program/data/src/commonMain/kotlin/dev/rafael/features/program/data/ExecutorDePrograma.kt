package dev.rafael.features.program.data

import dev.rafael.contract.program.ProgramDto
import dev.rafael.contract.program.RenameProgramRequest
import dev.rafael.contract.program.SetScheduleRequest
import dev.rafael.core.database.outbox.ExecutorDeOperacao
import dev.rafael.core.database.outbox.Operacao
import dev.rafael.core.database.outbox.TipoOperacao
import dev.rafael.core.network.httpResult
import dev.rafael.core.result.AppError
import dev.rafael.core.result.AppResult
import dev.rafael.core.result.asFailure
import dev.rafael.core.result.asSuccess
import kotlinx.serialization.json.Json

/**
 * Traduz as operações de PROGRAMA e AGENDA da fila em requisições (ARCH #30, B.4).
 *
 * `CRIAR_PROGRAMA` manda o id gerado no cliente — é o que torna o POST idempotente (B.1):
 * reenviar depois de uma resposta perdida devolve o programa existente em vez de criar um
 * segundo com o mesmo nome.
 */
class ExecutorDePrograma(
    private val remote: ProgramDataSource,
) : ExecutorDeOperacao {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    override val tipos = setOf(
        TipoOperacao.CRIAR_PROGRAMA,
        TipoOperacao.RENOMEAR_PROGRAMA,
        TipoOperacao.EXCLUIR_PROGRAMA,
        TipoOperacao.DEFINIR_AGENDA,
    )

    override suspend fun executar(operacao: Operacao): AppResult<Unit> =
        when (operacao.tipo) {
            TipoOperacao.EXCLUIR_PROGRAMA -> httpResult { remote.delete(operacao.alvoId) }

            TipoOperacao.CRIAR_PROGRAMA -> {
                val dto = decodificar(ProgramDto.serializer(), operacao.payload)
                    ?: return corrompida()
                httpResult { remote.createManual(dto.name, id = operacao.alvoId) }.paraUnit()
            }

            TipoOperacao.RENOMEAR_PROGRAMA -> {
                val req = decodificar(RenameProgramRequest.serializer(), operacao.payload)
                    ?: return corrompida()
                httpResult { remote.rename(operacao.alvoId, req.name) }.paraUnit()
            }

            TipoOperacao.DEFINIR_AGENDA -> {
                val req = decodificar(SetScheduleRequest.serializer(), operacao.payload)
                    ?: return corrompida()
                httpResult { remote.setSchedule(operacao.alvoId, req.entries) }.paraUnit()
            }

            else -> AppError.Unexpected("Tipo fora deste executor: ${operacao.tipo}").asFailure()
        }

    /**
     * A resposta do servidor é descartada de propósito. Quem regrava o local é o
     * `refresh()` do repositório, que roda logo depois com a lista COMPLETA — aplicar
     * aqui, programa a programa, daria dois caminhos de escrita para o mesmo dado.
     */
    private fun <T> AppResult<T>.paraUnit(): AppResult<Unit> = when (this) {
        is AppResult.Success -> Unit.asSuccess()
        is AppResult.Failure -> error.asFailure()
    }

    /** Ver ExecutorDeTreino.decodificar: payload ilegível vira falha permanente, não exceção. */
    private fun <T> decodificar(
        serializer: kotlinx.serialization.KSerializer<T>,
        payload: String,
    ): T? = runCatching { json.decodeFromString(serializer, payload) }.getOrNull()

    private fun corrompida(): AppResult<Unit> =
        AppError.Validation("Operação corrompida na fila").asFailure()
}
