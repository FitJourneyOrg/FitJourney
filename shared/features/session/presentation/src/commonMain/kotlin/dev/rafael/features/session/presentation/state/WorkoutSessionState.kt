package dev.rafael.features.session.presentation.state

import dev.rafael.core.result.AppError

/** Uma série editável na execução (reps/carga como texto p/ o input). */
data class SetEntry(
    val exerciseId: String,
    /** `null` quando o catálogo não conhece este id. Quem escreve a palavra é a TELA (G.3). */
    val exerciseName: String?,
    val orderIndex: Int,
    val setIndex: Int,
    val targetReps: Int,
    val repsDone: String,
    val weight: String,
    val done: Boolean,
    val restSeconds: Int,   // prescrito pelo motor (ARCH #26 §3.2)
    /** Mídia do catálogo, pra tela de execução. `null` = catálogo não conhece o id. */
    val thumbRef: String? = null,
    val videoRef: String? = null,
)

/** Uma série, com a posição dela em [WorkoutSessionState.entries] — os eventos são por índice. */
data class SerieEmExecucao(val indice: Int, val entrada: SetEntry)

/** Um exercício do treino, com as séries dele já agrupadas. */
data class ExercicioEmExecucao(
    val exerciseId: String,
    val nome: String?,
    val thumbRef: String?,
    val videoRef: String?,
    val restSeconds: Int,
    val series: List<SerieEmExecucao>,
) {
    val concluido: Boolean get() = series.all { it.entrada.done }
}

data class WorkoutSessionState(
    val workoutName: String = "",
    val entries: List<SetEntry> = emptyList(),
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val error: AppError? = null,
    val saved: Boolean = false,
    // --- descanso ---
    val restRemaining: Int? = null,   // segundos restantes; null = sem descanso rodando
    val restTotal: Int = 0,           // duração prescrita (p/ a barra de progresso)
    val restDoneTick: Int = 0,        // incrementa quando um descanso zera (UI vibra)
    val restPausado: Boolean = false,

    // --- execução um-exercício-por-vez (redesenho de 2026-10-01) ---
    /** Índice em [exercicios]. A tela mostra um de cada vez. */
    val exercicioAtual: Int = 0,
    /** Índice da série SELECIONADA dentro do exercício atual. */
    val serieAtual: Int = 0,
    /** Segundos desde que a tela abriu — o cronômetro do topo. */
    val segundosDaSessao: Int = 0,
) {
    val canFinish: Boolean get() = !isSaving && entries.any { it.done }

    /**
     * ⭐ O agrupamento é **derivado**, e isso é deliberado.
     *
     * [entries] é a lista plana de séries, e é ela que vai pro servidor no `finish()`. A tela nova
     * mostra um exercício por vez, mas **não ganha um modelo próprio**: ela lê esta projeção.
     *
     * > **Duas listas com o mesmo conteúdo divergem na primeira que alguém esquecer de atualizar.**
     *
     * Custo: recalcular a cada leitura, sobre algo como 30 séries. Irrelevante perto de ter dois
     * lugares guardando a mesma verdade.
     */
    val exercicios: List<ExercicioEmExecucao>
        get() = entries.withIndex()
            .groupBy { it.value.orderIndex }
            .entries
            .sortedBy { it.key }
            .map { (_, itens) ->
                val primeira = itens.first().value
                ExercicioEmExecucao(
                    exerciseId = primeira.exerciseId,
                    nome = primeira.exerciseName,
                    thumbRef = primeira.thumbRef,
                    videoRef = primeira.videoRef,
                    restSeconds = primeira.restSeconds,
                    series = itens
                        .sortedBy { it.value.setIndex }
                        .map { SerieEmExecucao(indice = it.index, entrada = it.value) },
                )
            }

    /** O exercício em foco, ou `null` enquanto carrega. */
    val exercicio: ExercicioEmExecucao? get() = exercicios.getOrNull(exercicioAtual)

    /** A série em foco dentro dele. */
    val serie: SerieEmExecucao? get() = exercicio?.series?.getOrNull(serieAtual)

    val temAnterior: Boolean get() = exercicioAtual > 0
    val temProximo: Boolean get() = exercicioAtual < exercicios.lastIndex

    /** "3/8" do topo: o exercício atual é o 1-based. */
    val totalDeExercicios: Int get() = exercicios.size
}

sealed interface SessionEvent {
    data class RepsChanged(val index: Int, val value: String) : SessionEvent
    data class WeightChanged(val index: Int, val value: String) : SessionEvent
    data class ToggleDone(val index: Int) : SessionEvent
    data object Finish : SessionEvent
    // descanso
    data object SkipRest : SessionEvent
    data class AddRest(val seconds: Int) : SessionEvent

    // --- execução um-exercício-por-vez ---
    data object ExercicioAnterior : SessionEvent
    data object ExercicioProximo : SessionEvent
    /** Toca num chip de série do exercício atual. */
    data class SerieSelecionada(val setIndex: Int) : SessionEvent
    /** Passo do `-`/`+` do peso. Ver `PASSO_DO_PESO_KG` no ViewModel. */
    data class AjustarPeso(val delta: Double) : SessionEvent
    data class AjustarReps(val delta: Int) : SessionEvent
    /** O ▶/⏸ do card: ocioso inicia o descanso, correndo pausa, pausado retoma. */
    data object AlternarDescanso : SessionEvent
}
