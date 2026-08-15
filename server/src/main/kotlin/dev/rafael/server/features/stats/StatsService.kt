package dev.rafael.server.features.stats

import dev.rafael.contract.stats.UserStatsDto
import dev.rafael.core.result.AppResult
import dev.rafael.core.result.flatMap
import dev.rafael.core.result.map
import dev.rafael.server.features.program.services.ProgramService
import dev.rafael.server.features.session.db.SessionRepository
import dev.rafael.server.features.user.services.UserService
import kotlinx.datetime.LocalDate
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

/**
 * Estatísticas do perfil (ARCH #16): XP, nível e sequência — SEMPRE derivados das sessões.
 *
 * Não há tabela de XP: o valor é recalculado a cada consulta a partir de `workout_sessions`.
 * Custa uma varredura do histórico do usuário (dezenas/centenas de linhas), o que é barato
 * agora e evita o pior problema de gamificação — saldo persistido que diverge da verdade.
 * Se o histórico crescer a ponto de pesar, materializar vira otimização (com o cálculo aqui
 * continuando como fonte da verdade).
 */
class StatsService(
    private val userService: UserService,
    private val sessions: SessionRepository,
    private val programs: ProgramService,
) {
    suspend fun forUser(firebaseUid: String, email: String?): AppResult<UserStatsDto> =
        userService.findOrCreate(firebaseUid, email).flatMap { user ->
            sessions.listByUser(user.id).flatMap { historico ->
                // dias de treino agendados (união dos programas) — protegem o descanso no streak
                programs.listForUser(user.id).map { progs ->
                    val diasDeTreino = progs.flatMap { p -> p.schedule.map { it.dayOfWeek } }.toSet()

                    // só sessões VÁLIDAS contam (ao menos 1 série marcada como feita)
                    val validas = historico
                        .map { it.finishedAt.date to it.sets.count { s -> s.done } }
                        .filter { (_, feitas) -> feitas > 0 }

                    val porDia: Map<LocalDate, List<Int>> = validas
                        .groupBy({ it.first }, { it.second })

                    val xp = XpPolicy.xpTotal(porDia)
                    val progresso = XpPolicy.progresso(xp)
                    val hoje = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
                    val semanaAtras = hoje.minus(DatePeriod(days = 7))

                    UserStatsDto(
                        xp = xp,
                        level = progresso.nivel,
                        xpInLevel = progresso.xpNoNivel,
                        xpForNextLevel = progresso.xpParaProximo,
                        streakDays = XpPolicy.streak(porDia.keys, diasDeTreino, hoje),
                        totalSessions = validas.size,
                        sessionsThisWeek = validas.count { (dia, _) -> dia > semanaAtras },
                        trainedToday = porDia.containsKey(hoje),
                        xpToday = XpPolicy.xpDoDia(porDia[hoje].orEmpty()),
                    )
                }
            }
        }
}
