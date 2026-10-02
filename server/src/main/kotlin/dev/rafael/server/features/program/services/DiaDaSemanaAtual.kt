package dev.rafael.server.features.program.services

import kotlin.time.Clock
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Pura, testável sem DI (recebe o [Clock] por parâmetro em vez de injetado — mesmo padrão de
 * [ProgramLimits]/[ProgramBlur]). Dois pontos de leitura precisam saber "que dia da semana é
 * hoje" pra resolver o treino ativo do programa (V60): [ProgramActiveWorkout] (GET /programs)
 * e [dev.rafael.server.features.workout.services.WorkoutService.get] (GET /workouts/{id}).
 *
 * `TimeZone.UTC`, não a do aparelho: autoridade do servidor ([REGRA]) — o mesmo padrão de
 * `AvisosDoDia`. O `kotlinx.datetime.DayOfWeek` deste projeto não tem `isoDayNumber` (isso é
 * `java.time.DayOfWeek`) -- o `when` explícito é o MESMO padrão já usado em
 * `XpPolicy.diaSemana` e no `HomeViewModel.diaDaSemanaHoje()` do cliente.
 */
object DiaDaSemanaAtual {
    fun iso(clock: Clock = Clock.System): Int =
        when (clock.now().toLocalDateTime(TimeZone.UTC).date.dayOfWeek) {
            DayOfWeek.MONDAY -> 1; DayOfWeek.TUESDAY -> 2; DayOfWeek.WEDNESDAY -> 3
            DayOfWeek.THURSDAY -> 4; DayOfWeek.FRIDAY -> 5; DayOfWeek.SATURDAY -> 6
            else -> 7
        }
}
