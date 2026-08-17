package dev.rafael.features.workout.data

import dev.rafael.contract.workout.WorkoutDto
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
 * Traduz as operações de TREINO da fila em requisições (ARCH #30, B.4).
 *
 * Vive aqui, e não no `core:database`, porque é quem conhece o `WorkoutDataSource` — o core
 * não pode depender de feature ([REGRA]).
 */
class ExecutorDeTreino(
    private val remote: WorkoutDataSource,
    private val local: WorkoutLocalDataSource,
) : ExecutorDeOperacao {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    override val tipos = setOf(
        TipoOperacao.CRIAR_TREINO,
        TipoOperacao.EDITAR_TREINO,
        TipoOperacao.EXCLUIR_TREINO,
    )

    override suspend fun executar(operacao: Operacao): AppResult<Unit> =
        when (operacao.tipo) {
            TipoOperacao.EXCLUIR_TREINO -> httpResult { remote.delete(operacao.alvoId) }

            TipoOperacao.CRIAR_TREINO, TipoOperacao.EDITAR_TREINO -> {
                val dto = decodificar(operacao.payload)
                    ?: return AppError.Validation("Operação corrompida na fila").asFailure()
                val enviado = if (operacao.tipo == TipoOperacao.CRIAR_TREINO) {
                    httpResult { remote.create(dto) }
                } else {
                    httpResult { remote.update(operacao.alvoId, dto) }
                }
                when (enviado) {
                    is AppResult.Success -> {
                        // Regrava com a resposta do SERVIDOR: [REGRA] a verdade é dele. É aqui
                        // que campos derivados (locked, contadores, timestamps reais) substituem
                        // a versão otimista que estava na tela.
                        local.save(operacao.alvoId, enviado.value)
                        Unit.asSuccess()
                    }
                    is AppResult.Failure -> enviado.error.asFailure()
                }
            }

            else -> AppError.Unexpected("Tipo fora deste executor: ${operacao.tipo}").asFailure()
        }

    /**
     * Payload gravado por uma versão anterior do app pode não desserializar mais. Devolver null
     * (→ Validation → falha permanente) é melhor que estourar dentro do worker: a exceção
     * travaria a fila inteira e nenhuma outra operação subiria nunca mais.
     */
    private fun decodificar(payload: String): WorkoutDto? =
        runCatching { json.decodeFromString(WorkoutDto.serializer(), payload) }.getOrNull()
}
