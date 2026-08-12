package dev.rafael.app.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.rafael.app.data.stats.StatsApi
import dev.rafael.contract.stats.UserStatsDto
import dev.rafael.core.result.AppResult
import dev.rafael.features.auth.domain.repository.AuthRepository
import dev.rafael.features.profile.domain.repository.ProfileRepository
import dev.rafael.features.program.domain.repository.ProgramRepository
import dev.rafael.features.workout.domain.repository.WorkoutRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    val error: String? = null,
    val today: TodayWorkout? = null,   // null + !isLoading + !semPrograma = dia de descanso
    val semPrograma: Boolean = false,  // usuário ainda não tem programa nenhum
    val stats: UserStatsDto? = null,   // XP/nível/streak (ARCH #16) — null = ainda não carregou
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
    private val stats: StatsApi,
) : ViewModel() {

    private val _loggedOut = MutableStateFlow(false)
    val loggedOut: StateFlow<Boolean> = _loggedOut.asStateFlow()

    private val _state = MutableStateFlow(HomeState())
    val state: StateFlow<HomeState> = _state.asStateFlow()

    fun load() {
        _state.update { it.copy(isLoading = true, error = null) }
        carregarStats()
        viewModelScope.launch {
            when (val r = programs.list()) {
                is AppResult.Failure ->
                    _state.update { it.copy(isLoading = false, error = r.error.message) }

                is AppResult.Success -> {
                    val hoje = diaDaSemanaHoje()
                    // programa mais recente que tenha treino agendado pra hoje
                    val achado = r.value.firstNotNullOfOrNull { p ->
                        p.schedule.firstOrNull { it.dayOfWeek == hoje }
                            ?.let { entry -> p to entry.workoutId }
                    }
                    if (achado == null) {
                        _state.update {
                            it.copy(isLoading = false, today = null, semPrograma = r.value.isEmpty())
                        }
                        return@launch
                    }
                    val (programa, workoutId) = achado
                    val resumo = programa.workouts.firstOrNull { it.id == workoutId }
                    // detalhe traz séries e descanso -> permite estimar a duração
                    val minutos = when (val w = workouts.get(workoutId)) {
                        is AppResult.Success -> estimarMinutos(
                            w.value.exercises.map { ex -> ex.sets.size to ex.restSeconds },
                        )
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
            }
        }
    }

    /**
     * XP/nível/streak em paralelo ao treino do dia. Falha aqui NÃO vira erro de tela:
     * a gamificação é acessório — o essencial (treinar hoje) não pode ficar refém dela.
     */
    private fun carregarStats() {
        viewModelScope.launch {
            when (val r = stats.get()) {
                is AppResult.Success -> _state.update { it.copy(stats = r.value) }
                is AppResult.Failure -> Unit   // silencioso: a faixa simplesmente não aparece
            }
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
