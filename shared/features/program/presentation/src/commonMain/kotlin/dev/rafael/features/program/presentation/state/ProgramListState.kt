package dev.rafael.features.program.presentation.state

import dev.rafael.features.program.domain.model.Program

data class ProgramListState(
    val programs: List<Program> = emptyList(),
    val isLoading: Boolean = false,
    val isCreating: Boolean = false,
    /** Erro de AÇÃO (ex.: falhou ao criar). Falha de sync NÃO entra aqui — ver `erroSync`. */
    val error: String? = null,
    /**
     * Falha na última sincronização (ARCH #30). Só é MOSTRADA quando não há nada local:
     * offline com dados na mão não é erro — a tela funciona normalmente.
     */
    val erroSync: String? = null,
    val sincronizouAlgumaVez: Boolean = false,
    val createdId: String? = null,   // sinaliza navegação pro detalhe do programa recém-criado
) {
    /** Vazio porque nunca sincronizou neste aparelho — não porque o usuário não tem programas. */
    val vazioPorFaltaDeSync: Boolean get() = programs.isEmpty() && erroSync != null && !sincronizouAlgumaVez
}
