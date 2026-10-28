package dev.rafael.server.features.exercise.engine.v3

import dev.rafael.contract.profile.Goal
import dev.rafael.contract.profile.Level

/**
 * Motor v3, etapa [2] — CAPACIDADE (`docs/trabalho/motor-v3-desenho-e-fatias.md` §2.1, Fatia 2).
 *
 * Mesma situação da Fatia 1: nada chama isto ainda. Fica parado até a Fatia 3 ligar o fio na
 * alocação.
 *
 * `teto = min(teto por tempo, teto por nível × fator de objetivo × fator de idade)`, e
 * `capacidade = dias × teto`. Números da especificação
 * (`propostas/motor-v3-ESPECIFICACAO.md`, "O princípio central: a capacidade é escassa" e
 * "Cobertura por faixa de tempo").
 *
 * **Teto em `Double`, não `Int` (decisão de `trabalho/motor-v3-fatia-2.md`).** Arredondar aqui
 * quebra a tabela de cobertura da especificação célula a célula — confirmado batendo as 27
 * células contra o documento. O arredondamento para séries inteiras é da Fatia 3 (alocação), na
 * ordem de prioridade — nunca nesta etapa.
 */
object CapacityModel {

    /** Teto fisiológico: nenhum músculo recebe mais que isto numa sessão, por mais que caiba. */
    const val MAX_SERIES_POR_MUSCULO_POR_SESSAO = 11

    /**
     * Overhead da rampa, em minutos, a partir do número de dias — não do `SplitType`.
     * Decisão do Rafael (`trabalho/motor-v3-fatia-2.md`): o motor v3 só gera as divisões que a
     * especificação cobre (2–3 dias = full body, quatro compostos de barra; 4–6 dias =
     * upper/lower, upper/lower+PPL ou PPL, dois compostos). `UPPER_LOWER_FULL` e `ARNOLD` são do
     * motor antigo e não entram aqui — por isso a função recebe `dias`, não `SplitType`, e um
     * `dias` fora do intervalo falha alto em vez de herdar 11min em silêncio.
     */
    fun overheadMinutos(dias: Int): Int = when (dias) {
        2, 3 -> 16
        4, 5, 6 -> 11
        else -> throw IllegalArgumentException(
            "motor v3 só cobre divisões de 2 a 6 dias por semana (especificação, " +
                "\"A divisão sai do número de dias\"); recebido: $dias"
        )
    }

    /** Teto por tempo: `(minutos − overhead) ÷ 3`. Nunca negativo. */
    fun tetoPorTempo(minutos: Int, dias: Int): Double =
        ((minutos - overheadMinutos(dias)) / 3.0).coerceAtLeast(0.0)

    /** Teto por nível: 16 iniciante, 18 intermediário e avançado. */
    fun tetoPorNivel(nivel: Level): Int = if (nivel == Level.BEGINNER) 16 else 18

    /** Fator de objetivo sobre o teto: manter 0,6; os demais (inclusive saúde geral) 1,0. */
    fun fatorObjetivo(goal: Goal): Double = if (goal == Goal.MAINTAIN) 0.6 else 1.0

    /**
     * `mesocicloAtual == null` conta como 1º mesociclo — o app ainda não acompanha mesociclos
     * (chega na Fatia 10). Convenção `[REGRA]` da Fatia 2, compartilhada pelo fator de idade e
     * pelo modo conservador automático ([SafetyRules], Fatia 7). Espelhada no oráculo Python
     * como `primeiro_mesociclo()` em `server/src/test/oracle/motor_v3.py`.
     */
    fun primeiroMesociclo(mesocicloAtual: Int?): Boolean = (mesocicloAtual ?: 1) == 1

    /**
     * Fator de idade sobre o teto: 0,75 para 60+ que seja iniciante OU esteja no 1º mesociclo.
     * É o lado seguro: reduzir volume para quem tem 60+ começando é o objetivo da regra, e a
     * regra em si não muda quando a Fatia 10 passar a enviar o mesociclo real.
     */
    fun fatorIdade(idade: Int?, nivel: Level, mesocicloAtual: Int?): Double {
        if (idade == null || idade < 60) return 1.0
        return if (nivel == Level.BEGINNER || primeiroMesociclo(mesocicloAtual)) 0.75 else 1.0
    }

    /** `min(teto por tempo, teto por nível × fator de objetivo × fator de idade)`. */
    fun tetoDaSessao(
        minutos: Int,
        dias: Int,
        nivel: Level,
        goal: Goal,
        idade: Int?,
        mesocicloAtual: Int?,
    ): Double {
        val porTempo = tetoPorTempo(minutos, dias)
        val porNivel = tetoPorNivel(nivel) * fatorObjetivo(goal) * fatorIdade(idade, nivel, mesocicloAtual)
        return minOf(porTempo, porNivel)
    }

    /** Capacidade semanal: `dias × teto da sessão`. */
    fun capacidadeSemanal(
        minutos: Int,
        dias: Int,
        nivel: Level,
        goal: Goal,
        idade: Int?,
        mesocicloAtual: Int?,
    ): Double = dias * tetoDaSessao(minutos, dias, nivel, goal, idade, mesocicloAtual)
}
