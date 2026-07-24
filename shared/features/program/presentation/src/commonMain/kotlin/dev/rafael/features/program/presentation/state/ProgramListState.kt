package dev.rafael.features.program.presentation.state

import dev.rafael.features.program.domain.model.Program

data class ProgramListState(
    val programs: List<Program> = emptyList(),
    val isLoading: Boolean = false,
    val isCreating: Boolean = false,
    val error: String? = null,
    val createdId: String? = null,   // sinaliza navegação pro detalhe do programa recém-criado
)
