package dev.rafael.contract.stats

import kotlinx.serialization.Serializable

/**
 * Gamificação do perfil individual (ARCH #16): XP, nível e sequência.
 *
 * [REGRA] Tudo é DERIVADO das sessões no servidor — o cliente só exibe. Não existe campo
 * de XP que o cliente possa enviar; por isso não há risco de saldo divergente.
 *
 * `streakDays` conta dias cumprindo o plano: dia com sessão OU dia de descanso agendado.
 */
@Serializable
data class UserStatsDto(
    val xp: Int,                 // XP total acumulado
    val level: Int,              // nível atual (começa em 1)
    val xpInLevel: Int,          // XP já feito dentro do nível atual
    val xpForNextLevel: Int,     // XP que este nível custa (denominador da barra)
    val streakDays: Int,
    val totalSessions: Int,      // sessões válidas (com ao menos 1 série feita)
    val sessionsThisWeek: Int,
    // Hoje: a Home usa pra mostrar "treino concluído" em vez de oferecer o mesmo treino de novo.
    val trainedToday: Boolean = false,
    val xpToday: Int = 0,
)
