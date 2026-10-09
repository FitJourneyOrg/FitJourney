package dev.rafael.features.stats.domain

import dev.rafael.contract.stats.ExercicioDetalheDto
import dev.rafael.core.result.AppResult

/**
 * Detalhe de UM exercicio dentro de UM programa (J.5) — a tela que "ver detalhado" abre a
 * partir da lista de exercicios do recorte de programa.
 *
 * ## ONLINE-ONLY, sem cache — diferente do [Progresso]
 *
 * A tela de Progress e reaberta a cada `onResume`, entao cachear e o que da os graficos no
 * primeiro frame. Esta tela abre uma vez por toque em "ver detalhado" — cachear multiplicaria
 * por (programas x exercicios) sem a mesma leitura repetida que justifica o cache da outra.
 * Mesmo padrao do `ProgramRepository.activateProgram`: sem otimismo, sem Flow, so
 * pedido-e-resposta.
 *
 * [REGRA] ARCH #16: o calculo e do SERVIDOR. So leitura.
 */
interface DetalheDeExercicio {
    suspend fun buscar(programId: String, exercicioId: String): AppResult<ExercicioDetalheDto>
}
