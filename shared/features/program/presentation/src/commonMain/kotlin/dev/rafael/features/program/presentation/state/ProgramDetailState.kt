package dev.rafael.features.program.presentation.state

import dev.rafael.core.result.AppError
import dev.rafael.features.program.domain.model.Program

data class ProgramDetailState(
    val program: Program? = null,
    val isLoading: Boolean = false,
    val isRenaming: Boolean = false,
    val isReordering: Boolean = false,   // durante o PUT /schedule (desabilita as setas)
    val error: AppError? = null,
    val isDeleted: Boolean = false,   // sinaliza pra tela voltar pra lista
)
