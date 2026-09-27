package dev.rafael.features.program.presentation.state

import dev.rafael.core.result.AppError
import dev.rafael.features.program.domain.model.PendenciaDeSync
import dev.rafael.features.program.domain.model.Program

data class ProgramDetailState(
    val program: Program? = null,
    val isLoading: Boolean = false,
    val isRenaming: Boolean = false,
    val isReordering: Boolean = false,   // durante o PUT /schedule (desabilita as setas)
    val error: AppError? = null,
    val isDeleted: Boolean = false,   // sinaliza pra tela voltar pra lista
    /**
     * V59, migrado do ProgramListScreen (restauração da hierarquia original: lista de
     * programas > detalhe > semana > treino). "Ativar" agora é ação de dentro do
     * programa dono do treino, não mais de uma lista achatada cross-program.
     */
    val pendencias: Set<PendenciaDeSync> = emptySet(),
    /** id do treino em ativação neste instante (desabilita o botão, evita duplo toque). */
    val activating: String? = null,
) {
    fun pendenciaDe(workoutId: String?): PendenciaDeSync? =
        workoutId?.let { id -> pendencias.firstOrNull { it.alvoId == id } }
}
