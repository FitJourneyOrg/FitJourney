package dev.rafael.contract.stats

import dev.rafael.contract.profile.MuscleGroup
import kotlinx.serialization.Serializable

/**
 * Analise de progressao (J.2). Uma rota so, com os blocos pagos nulos para quem e free.
 *
 * ## Por que campo nulo e nao 403
 *
 * O precedente da casa e o 403 com codigo de portao ([dev.rafael.contract.error.ErrorCodes]),
 * e ele continua valendo para ACAO bloqueada - gerar programa, abrir treino trancado. Aqui nao
 * ha acao: a tela mistura blocos gratis e pagos, e separar em duas chamadas custaria um estado
 * de erro que nao e erro. Entao o portao vira ausencia de dado.
 *
 * ## [INV] Bloco pago nulo NAO pode carregar o numero em outro campo
 *
 * E o mesmo buraco que o `ProgramAccess` nasceu para fechar: a listagem trancava os dias e o
 * `GET /workouts/{id}` entregava o conteudo a quem pedisse pelo id. Resposta que tranca na tela
 * e entrega no JSON nao tranca nada.
 *
 * ## [analysisLocked] existe para o cliente nao mentir
 *
 * Nulo tem duas causas: "voce e free" e "voce ainda nao tem dado". Sem distinguir, um premium
 * recem-chegado veria o paywall de algo que ele ja pagou - o erro que o `ProgramLimits` evita
 * no outro sentido ao NAO mandar premium para o paywall. Com a flag, o cliente escolhe entre
 * paywall e estado vazio.
 */
@Serializable
data class ProgressDto(
    // ---- sempre visivel ----
    val totalKg: Double,
    val totalSessions: Int,
    /** Data (ISO) da sessao com carga mais antiga. `null` quando nunca houve nenhuma. */
    val sinceDate: String? = null,
    val lastVsPrevious: WorkoutComparisonDto? = null,

    // ---- premium ----
    val weeklyLoad: List<WeeklyLoadDto>? = null,
    val strengthTrend: List<ExerciseTrendDto>? = null,
    val setsByMuscle: MuscleVolumeDto? = null,

    /** `true` = os tres blocos acima sao nulos por PLANO, nao por falta de dado. */
    val analysisLocked: Boolean = false,

    // ---- filtro (J.3) ----
    /**
     * Ids dos programas que TEM sessao no historico — o que o filtro pode oferecer.
     *
     * So ids: o NOME do programa e derivado no cliente a partir de `daysPerWeek` + `split`
     * (V48/ARCH #37), e mandar o nome daqui seria re-persistir texto traduzido. O cliente cruza
     * esta lista com os programas que ja tem em cache.
     */
    val availablePrograms: List<String> = emptyList(),

    /**
     * Existe sessao fora de programa — `program_id` nulo **ou** apontando para programa apagado.
     *
     * Os dois casos viram o mesmo balde porque o segundo **nao tem como ser nomeado**: o programa
     * sumiu, e o nome dele era derivado dele. Oferecer "Programa removido" como se fosse um item
     * seria inventar identidade para algo que nao existe mais.
     */
    val hasUnassigned: Boolean = false,

    /** Semanas disponiveis do programa filtrado — o teto do seletor de faixa. `null` em "todos". */
    val programWeeks: Int? = null,

    /** Faixa aplicada (1-based, inclusiva). `null` em "todos". */
    val fromWeek: Int? = null,
    val toWeek: Int? = null,
    /**
     * A janela de calendario APLICADA, em semanas (8, 26 ou 52). Nula no recorte de programa,
     * onde quem define o periodo e a faixa [fromWeek]..[toWeek].
     *
     * ⚠️ E a aplicada, nao a pedida: o free pedindo 52 recebe 8 aqui, e a tela tem de refletir
     * isso. Seletor marcando 52 com o eixo desenhando 8 e a mesma mentira do rotulo "esta
     * semana" numa faixa que termina semanas atras.
     */
    val weeksWindow: Int? = null,
)

/**
 * Carga total de uma semana.
 *
 * ⚠️ **Duas reguas, e a resposta diz qual esta em uso.** Sem filtro de programa, `weekStart` e a
 * segunda-feira ISO e `weekNumber` vem nulo. Com um programa escolhido, `weekNumber` e a semana
 * DELE (1-based, contada em dias corridos a partir do `started_at`) e e esse o rotulo do eixo.
 * Misturar as duas e o que faz "semana 10" nao significar nada.
 */
@Serializable
data class WeeklyLoadDto(
    val weekStart: String,
    val kg: Double,
    val weekNumber: Int? = null,
)

/**
 * Evolucao de UM exercicio em 1RM estimado (Epley).
 *
 * [name] viaja ja traduzido - e nome de catalogo, nao enum, entao nao da para resolver no
 * cliente por id.
 */
@Serializable
data class ExerciseTrendDto(
    val exerciseId: String,
    val name: String,
    val points: List<TrendPointDto>,
    /** Variacao percentual do primeiro ao ultimo ponto. */
    val changePercent: Double,
)

@Serializable
data class TrendPointDto(
    val weekStart: String,
    val estimated1rm: Double,
    /** Semana do programa, quando ha filtro. Mesma regra do [WeeklyLoadDto.weekNumber]. */
    val weekNumber: Int? = null,
)

/**
 * Media semanal de SERIES por grupo. Series, nao quilos: carga nao e comparavel entre
 * maquina e halter (ver `ProgressPolicy.seriesPorGrupo`).
 *
 * [unclassified] e o volume de exercicio sem `primary_muscles` no catalogo - aparece para nao
 * sumir em silencio.
 */
@Serializable
data class MuscleVolumeDto(
    val byMuscle: Map<MuscleGroup, Double>,
    val unclassified: Double = 0.0,
)

/** Ultima sessao contra a anterior DO MESMO treino. */
@Serializable
data class WorkoutComparisonDto(
    val workoutName: String,
    val currentDate: String,
    val previousDate: String,
    val currentKg: Double,
    val previousKg: Double,
    val exercises: List<ExerciseDeltaDto>,
)

@Serializable
data class ExerciseDeltaDto(
    val exerciseId: String,
    val name: String,
    val currentKg: Double,
    val deltaKg: Double,
)
