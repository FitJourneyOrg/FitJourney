package dev.rafael.app.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.rafael.app.data.session.SessionSync
import dev.rafael.app.data.stats.StatsRepository
import dev.rafael.contract.stats.UserStatsDto
import dev.rafael.core.result.AppError
import dev.rafael.core.result.AppResult
import dev.rafael.features.auth.domain.repository.AuthRepository
import dev.rafael.features.profile.domain.repository.ProfileRepository
import dev.rafael.features.program.domain.model.Program
import dev.rafael.features.program.domain.repository.ProgramRepository
import dev.rafael.features.workout.domain.repository.WorkoutRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/** O treino agendado para hoje (resolvido pelo schedule do programa). */
data class TodayWorkout(
    val workoutId: String,
    val name: String,
    val programName: String,
    val exerciseCount: Int,
    val minutes: Int,          // estimativa (ver estimarMinutos)
    val locked: Boolean,       // dia trancado p/ não-premium (ARCH #23)
    val week: Int,
    val totalWeeks: Int,
)

data class HomeState(
    val isLoading: Boolean = true,
    val error: AppError? = null,
    val today: TodayWorkout? = null,   // null + !isLoading + !semPrograma = dia de descanso
    val semPrograma: Boolean = false,  // usuário ainda não tem programa nenhum
    /**
     * Última falha de sincronização, como AppError (não String). Com lista vazia, distingue
     * "sem programa" de "sem sync" — e o TEXTO fica a cargo da UI, que sabe se o aparelho está
     * offline ou se foi o servidor que caiu. Ver app/ui/ErrorUi.kt.
     */
    val erroSync: AppError? = null,
    val stats: UserStatsDto? = null,   // XP/nível/streak (ARCH #16) — null = ainda não carregou
    /**
     * Se já treinou hoje. Vem do BANCO LOCAL (não do servidor): funciona offline e é imediato
     * — o usuário acabou de finalizar o treino, a Home não pode depender de rede pra saber.
     */
    val treinouHoje: Boolean = false,
    /** Sessões ainda não enviadas. O XP delas só entra quando subirem (autoridade do servidor). */
    val sessoesPendentes: Int = 0,
)

/**
 * VM do hub (Home): resolve o TREINO DE HOJE e cuida do logout.
 *
 * Como o "hoje" é resolvido: o programa guarda `schedule` (workoutId → dayOfWeek, 1=Seg..7=Dom).
 * Comparamos com o dia da semana local do aparelho — aqui o relógio do cliente é aceitável
 * porque é só apresentação; nada de XP/validação depende disso (autoridade do servidor).
 * Sem treino agendado para hoje = dia de descanso (descanso é implícito, ARCH #22).
 */
class HomeViewModel(
    private val auth: AuthRepository,
    private val profile: ProfileRepository,
    private val programs: ProgramRepository,
    private val workouts: WorkoutRepository,
    private val stats: StatsRepository,
    private val sessions: SessionSync,
) : ViewModel() {

    private val _loggedOut = MutableStateFlow(false)
    val loggedOut: StateFlow<Boolean> = _loggedOut.asStateFlow()

    private val _state = MutableStateFlow(HomeState())
    val state: StateFlow<HomeState> = _state.asStateFlow()

    init {
        // Observa o histórico LOCAL: assim que a sessão é gravada (mesmo offline), a Home
        // já sabe que o treino de hoje foi feito e troca o card — sem esperar servidor.
        sessions.observarHistorico()
            .onEach { historico ->
                val hoje = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date.toString()
                val pendentesAgora = historico.count { s -> s.pendente }
                val pendentesAntes = _state.value.sessoesPendentes
                _state.update {
                    it.copy(
                        treinouHoje = historico.any { s -> s.dto.finishedAt.startsWith(hoje) },
                        sessoesPendentes = pendentesAgora,
                    )
                }
                // A pendência CAIU: o WorkManager sincronizou em background (talvez com a tela
                // aberta). O servidor já recalculou o XP — busca de novo pra a faixa atualizar
                // sozinha, sem o usuário precisar sair e voltar da tela.
                if (pendentesAgora < pendentesAntes) {
                    viewModelScope.launch { stats.sincronizar(forcar = true) }   // o XP mudou: ignora o TTL
                }
            }
            .launchIn(viewModelScope)

        // Faixa de XP vem do CACHE local: aparece sempre, offline inclusive.
        stats.observar()
            .onEach { s -> _state.update { it.copy(stats = s) } }
            .launchIn(viewModelScope)

        // Programas do BANCO LOCAL (ARCH #30): o card do dia pinta na hora, offline inclusive,
        // e se atualiza sozinho quando o sync grava algo novo.
        programs.observePrograms()
            .onEach { lista -> resolverTreinoDeHoje(lista) }
            .launchIn(viewModelScope)
    }

    /** Acha o treino agendado para hoje entre os programas locais. */
    private suspend fun resolverTreinoDeHoje(programas: List<Program>) {
        val hoje = diaDaSemanaHoje()
        val achado = programas.firstNotNullOfOrNull { p ->
            p.schedule.firstOrNull { it.dayOfWeek == hoje }?.let { e -> p to e.workoutId }
        }
        if (achado == null) {
            _state.update { it.copy(isLoading = false, today = null, semPrograma = programas.isEmpty()) }
            return
        }
        val (programa, workoutId) = achado
        val resumo = programa.workouts.firstOrNull { it.id == workoutId }
        // Dia trancado (ARCH #23): não busca o detalhe. O servidor recusa com 403 — o card
        // trancado não mostra duração de qualquer forma, e pedir o que já se sabe negado só
        // enche o log de 403 que parecem bug.
        val minutos = if (resumo?.locked == true) 0 else when (val w = workouts.get(workoutId)) {
            is AppResult.Success -> estimarMinutos(w.value.exercises.map { it.sets.size to it.restSeconds })
            is AppResult.Failure -> 0
        }
        _state.update {
            it.copy(
                isLoading = false,
                semPrograma = false,
                today = TodayWorkout(
                    workoutId = workoutId,
                    name = resumo?.name ?: "Treino de hoje",
                    programName = programa.name,
                    exerciseCount = resumo?.exerciseCount ?: 0,
                    minutes = minutos,
                    locked = resumo?.locked == true,
                    week = programa.currentWeek,
                    totalWeeks = programa.durationWeeks,
                ),
            )
        }
    }

    /**
     * A tela NÃO carrega dado aqui — ela já observa o banco (ver init). Isto é só o SYNC:
     * atualiza o local com o servidor e, se algo mudar, o Flow re-emite (ARCH #30).
     * Falha de rede não vira erro de tela: o usuário segue vendo o que tem.
     */
    fun load() {
        carregarStats()
        viewModelScope.launch {
            // Guarda a falha p/ a tela distinguir "você ainda não tem programa" de
            // "não consegui sincronizar". Convidar alguém que JÁ TEM programas a criar
            // tudo de novo é o pior conselho possível.
            val r = programs.list()
            _state.update {
                it.copy(erroSync = (r as? AppResult.Failure)?.error)
            }
        }
    }

    /**
     * XP/nível/streak em paralelo ao treino do dia. Falha aqui NÃO vira erro de tela:
     * a gamificação é acessório — o essencial (treinar hoje) não pode ficar refém dela.
     */
    private fun carregarStats() {
        viewModelScope.launch {
            // A Home não baixa o histórico: quem faz isso é o boot (Splash), a tela de
            // Progresso e o SyncWorker. Aqui só sobe pendência e atualiza o XP.
            sessions.flush()
            stats.sincronizar()   // grava as stats no cache; o Flow re-emite
        }
    }

    fun logout() {
        viewModelScope.launch {
            profile.clearOnboardingCache()   // evita o próximo cadastro herdar o 'true' e cair na Home
            auth.signOut()                   // limpa a sessão + invalida o token cacheado do Ktor
            _loggedOut.value = true
        }
    }

    private fun diaDaSemanaHoje(): Int {
        val d = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date.dayOfWeek
        return when (d) {                    // schedule usa 1=Seg..7=Dom
            DayOfWeek.MONDAY -> 1; DayOfWeek.TUESDAY -> 2; DayOfWeek.WEDNESDAY -> 3
            DayOfWeek.THURSDAY -> 4; DayOfWeek.FRIDAY -> 5; DayOfWeek.SATURDAY -> 6
            else -> 7
        }
    }

    private companion object {
        /** ~40s de execução por série (estimativa de trabalho efetivo). */
        const val SEGUNDOS_POR_SERIE = 40

        /**
         * Duração estimada: por exercício, (séries × execução) + (séries−1 × descanso prescrito).
         * O descanso vem do motor (ARCH #26 §3.2), então a conta acompanha a prescrição real:
         * treino de composto pesado (150s) estima mais que um de isolamento (75s).
         * Arredonda para múltiplos de 5 min — é estimativa, não cronômetro.
         */
        fun estimarMinutos(exercicios: List<Pair<Int, Int>>): Int {
            val segundos = exercicios.sumOf { (series, descanso) ->
                series * SEGUNDOS_POR_SERIE + (series - 1).coerceAtLeast(0) * descanso
            }
            val min = (segundos + 59) / 60
            return ((min + 2) / 5) * 5
        }
    }
}
