package dev.rafael.server.features.stats

import kotlinx.datetime.LocalDate
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.minus

/**
 * Política de XP, nível e streak (ARCH #16 — gamificação é do PERFIL INDIVIDUAL).
 *
 * [REGRA] Autoridade do servidor: XP é SEMPRE derivado das sessões aqui. O cliente nunca
 * calcula nem envia XP — só exibe. Como é derivado, é recalculável e auditável a qualquer
 * momento (não há saldo mutável a corromper).
 *
 * Kotlin puro, sem I/O: dá pra testar a regra inteira sem banco.
 */
object XpPolicy {

    const val XP_POR_SERIE = 10          // recompensa o trabalho real (série marcada como feita)
    const val XP_POR_TREINO = 50         // recompensa terminar o treino
    const val TETO_DIARIO = 500          // anti-farming: registrar N sessões no mesmo dia não multiplica

    /** XP de UMA sessão. Sessão sem série feita não vale nada (nem conta como treino). */
    fun xpDaSessao(seriesFeitas: Int): Int =
        if (seriesFeitas <= 0) 0 else seriesFeitas * XP_POR_SERIE + XP_POR_TREINO

    /** XP do dia, com teto. `sessoesDoDia` = séries feitas de cada sessão daquele dia. */
    fun xpDoDia(sessoesDoDia: List<Int>): Int =
        sessoesDoDia.sumOf { xpDaSessao(it) }.coerceAtMost(TETO_DIARIO)

    /** XP total = soma dos dias (cada dia já com o teto aplicado). */
    fun xpTotal(porDia: Map<LocalDate, List<Int>>): Int =
        porDia.values.sumOf { xpDoDia(it) }

    // ---- níveis -----------------------------------------------------------
    // Custo do nível N = 1000 + 250*(N-1): cada nível custa 250 a mais que o anterior.
    // Nível 2 em ~4 treinos; nível 12 em ~100 treinos (~6 meses a 4x/semana).

    fun custoDoNivel(nivel: Int): Int = 1000 + 250 * (nivel - 1)

    data class Progresso(val nivel: Int, val xpNoNivel: Int, val xpParaProximo: Int)

    fun progresso(xpTotal: Int): Progresso {
        var nivel = 1
        var restante = xpTotal.coerceAtLeast(0)
        while (restante >= custoDoNivel(nivel)) {
            restante -= custoDoNivel(nivel)
            nivel++
        }
        return Progresso(nivel = nivel, xpNoNivel = restante, xpParaProximo = custoDoNivel(nivel))
    }

    // ---- streak -----------------------------------------------------------

    /**
     * Sequência de dias cumprindo o plano. [REGRA ratificada] Um dia conta quando:
     *   (a) houve sessão registrada, OU
     *   (b) era dia de DESCANSO pelo schedule (descanso não quebra a sequência).
     *
     * Motivo: contar só "dias treinados" empurraria o usuário a treinar todo dia pra não
     * perder o streak — o oposto do que o app prescreve (descanso é parte do plano, #22/#26).
     *
     * Hoje ainda "em aberto": se hoje é dia de treino e ainda não houve sessão, não conta,
     * mas também NÃO quebra — o dia não terminou.
     *
     * @param diasComSessao datas (locais) que têm ao menos uma sessão válida
     * @param diasDeTreino  dias da semana agendados (1=Seg..7=Dom). Vazio = sem programa:
     *                      aí só sessão conta (não há descanso planejado a proteger).
     */
    fun streak(diasComSessao: Set<LocalDate>, diasDeTreino: Set<Int>, hoje: LocalDate): Int {
        if (diasComSessao.isEmpty()) return 0
        val primeira = diasComSessao.min()

        fun cumpriu(d: LocalDate): Boolean =
            d in diasComSessao || (diasDeTreino.isNotEmpty() && diaSemana(d) !in diasDeTreino)

        var total = 0
        var dia = hoje
        // hoje: conta se cumpriu; se é treino pendente, pula sem quebrar
        if (cumpriu(dia)) total++
        dia = dia.minus(DatePeriod(days = 1))

        while (dia >= primeira && total < 365) {
            if (!cumpriu(dia)) break
            total++
            dia = dia.minus(DatePeriod(days = 1))
        }
        return total
    }

    private fun diaSemana(d: LocalDate): Int = when (d.dayOfWeek) {
        DayOfWeek.MONDAY -> 1; DayOfWeek.TUESDAY -> 2; DayOfWeek.WEDNESDAY -> 3
        DayOfWeek.THURSDAY -> 4; DayOfWeek.FRIDAY -> 5; DayOfWeek.SATURDAY -> 6
        else -> 7
    }
}
