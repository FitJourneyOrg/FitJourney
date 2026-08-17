package dev.rafael.features.program.presentation.state

import dev.rafael.core.result.AppError
import dev.rafael.features.program.domain.model.PendenciaDeSync
import dev.rafael.features.program.domain.model.Program

/**
 * Os erros viajam como [AppError], não como String já formatada. O texto depende de coisas que
 * só a camada de UI sabe — se o aparelho está offline ou se foi o servidor que caiu, por
 * exemplo. Achatar pra String aqui joga essa decisão fora (ver app/ui/ErrorUi.kt).
 */
data class ProgramListState(
    val programs: List<Program> = emptyList(),
    val isLoading: Boolean = false,
    val isCreating: Boolean = false,
    /** Erro de AÇÃO (ex.: falhou ao criar) → snackbar. Falha de sync NÃO entra aqui. */
    val error: AppError? = null,
    /**
     * Falha na última sincronização (ARCH #30). Só é MOSTRADA quando não há nada local:
     * offline com dados na mão não é erro — a tela funciona normalmente.
     */
    val erroSync: AppError? = null,
    /**
     * Já baixou programas neste aparelho, com esta conta. Vem do carimbo PERSISTIDO
     * (`SyncStamps`), não do ciclo de vida do ViewModel: quando era só um campo em memória,
     * todo cold start offline voltava a false e a tela dizia "Sem conexão" para quem já tinha
     * baixado tudo no dia anterior.
     */
    val sincronizouAlgumaVez: Boolean = false,
    val createdId: String? = null,   // sinaliza navegação pro detalhe do programa recém-criado
    /**
     * O que ainda não subiu (ARCH #30, B.4), por id. Com escrita otimista o usuário não tem
     * como distinguir "salvo no servidor" de "salvo só aqui" — o selo é o que torna o
     * otimismo honesto.
     */
    val pendencias: Set<PendenciaDeSync> = emptySet(),
) {
    fun pendenciaDe(programId: String?): PendenciaDeSync? =
        programId?.let { id -> pendencias.firstOrNull { it.alvoId == id } }

    /** Vazio porque nunca sincronizou neste aparelho — não porque o usuário não tem programas. */
    val vazioPorFaltaDeSync: Boolean get() = programs.isEmpty() && erroSync != null && !sincronizouAlgumaVez
}
