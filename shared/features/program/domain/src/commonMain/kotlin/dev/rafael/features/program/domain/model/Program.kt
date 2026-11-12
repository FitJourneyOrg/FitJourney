package dev.rafael.features.program.domain.model

/**
 * Programa (ARCH #22, revisado pela #27 — usuário pode ter vários, não é mais
 * 1 ativo que se substitui). Agrupa N treinos.
 */
data class Program(
    val id: String?,               // null só em estados transitórios; server sempre preenche na resposta
    val name: String,
    val workouts: List<ProgramWorkout>,
    val daysPerWeek: Int,
    // Chave do SplitType do contrato (ex. "FULL_BODY"), NAO o enum tipado -- este modulo
    // (:domain) nao depende de shared-contract, e nenhum :domain do projeto depende hoje
    // ([REGRA] domain e Kotlin puro e nao conhece nem outra feature). null = programa MANUAL
    // (shell sem motor). Quem resolve pro enum e traduz e a camada de app -- ver
    // `rotuloDoSplit()`/`rationaleDoPrograma()` em ui/, mesmo padrao defensivo
    // (`runCatching { X.valueOf(...) }`) que ja existia pro WorkoutOrigin no
    // ProgramLocalDataSource.
    val split: String?,
    // Chaves de MuscleGroup (contrato) priorizadas NO MOMENTO da geracao (fatia "rationale
    // derivado", 2026-09-22) -- mesma razao do split acima pra ficar String em vez do enum.
    // Vazio = sem foco, ou programa anterior a esta fatia (legado, sem snapshot).
    val focusMuscles: List<String> = emptyList(),
    // Débito (c)/(d) de "o motor sabe, o app não mostra" (2026-10-31, V73): declaração de
    // custo do motor. Chave String (TargetMuscle.name), não enum -- domain não conhece
    // contrato. Vazio = legado OU motor atual (StructureEngine), que ainda não calcula isto
    // -- dormente até o v3 virar o gerador de produção.
    val avisos: List<ProgramAviso> = emptyList(),
    val grupamentosReduzidosPeloFoco: List<String> = emptyList(),
    val locked: Boolean,
    // V60 (reverte a V59): true pro programa marcado como ativo (ponteiro exclusivo,
    // autoridade do servidor).
    val isActive: Boolean = false,
    val schedule: List<ProgramScheduleEntry>,
    val durationWeeks: Int = 8,   // janela do cronograma (ARCH #22)
    val currentWeek: Int = 1,     // semana atual, derivada no servidor
    val createdAt: String?,
    val updatedAt: String?,
)

/**
 * Treino dentro de um programa — versão enxuta (não reusa workout:domain.Workout de
 * propósito: Konsist trata domain de todas as features como 1 camada única e proíbe
 * dependência entre elas). Detalhe completo (exercícios/séries) mora na feature workout;
 * a tela navega pro WorkoutDetailScreen pra ver isso.
 */
data class ProgramWorkout(
    val id: String?,
    val name: String,
    val exerciseCount: Int,
    val locked: Boolean = false,   // ARCH #23: dia trancado (não-premium). exerciseCount = quantos há por trás.
    // V60 (reverte a V59): true pro treino que cai no dia de hoje DENTRO do programa ativo --
    // derivado no servidor (schedule x dia da semana), não mais ponteiro direto por treino.
    val isActive: Boolean = false,
)

data class ProgramScheduleEntry(
    val workoutId: String,
    val dayOfWeek: Int,
)

/**
 * Um aviso de honestidade por músculo. `musculo` é a chave `TargetMuscle` do motor (não
 * `MuscleGroup`); `causa` é a chave de `CausaAvisoDto`/`ProgramAudit.Causa`. A camada de
 * app/ui.android traduz as duas via `Rotulos.kt`, mesmo padrão do `split` acima.
 */
data class ProgramAviso(
    val musculo: String,
    val causa: String,
)
