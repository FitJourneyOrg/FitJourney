package dev.rafael.server.features.exercise.engine.v3

import dev.rafael.server.features.exercise.engine.TargetMuscle

/**
 * Motor v3, etapa [4] — ESTRUTURA DA SEMANA (`docs/trabalho/motor-v3-desenho-e-fatias.md` §3,
 * Fatia 4). Espelha `tools/motor_sim/motor_v3.py::montar_semana()`/`espalhar()` e
 * `spec_v3.py::DIVISOES`, validado rodando o Python com as mesmas saídas de `alocar()` (Fatia 3)
 * — não com números inventados (`docs/trabalho/motor-v3-fatia-4.md`).
 *
 * O calendário (que dia da semana é cada posição) não é desta fatia — `divisao()` já devolve
 * quais músculos cada posição treina, que é a decisão de motor; rotular a posição como
 * "segunda"/"quinta" não muda volume nem frequência.
 */
object WeekStructure {

    /** Uma posição no ciclo semanal: rótulo (só documentação) e quais músculos ela treina. */
    data class Dia(val rotulo: String, val musculos: Set<TargetMuscle>)

    private val UPPER = setOf(
        TargetMuscle.CHEST, TargetMuscle.BACK, TargetMuscle.SHOULDERS,
        TargetMuscle.BICEPS, TargetMuscle.TRICEPS, TargetMuscle.FOREARMS,
    )
    private val LOWER = setOf(
        TargetMuscle.QUADS, TargetMuscle.POSTERIOR, TargetMuscle.CALVES,
        TargetMuscle.GLUTES,
    )
    private val PUSH = setOf(TargetMuscle.CHEST, TargetMuscle.SHOULDERS, TargetMuscle.TRICEPS)
    private val PULL = setOf(TargetMuscle.BACK, TargetMuscle.BICEPS, TargetMuscle.FOREARMS)
    private val FULL = TargetMuscle.entries.toSet()

    /** "A divisão sai do número de dias" — só as recomendadas (★), espelha `spec_v3.py::DIVISOES`. */
    private val DIVISOES: Map<Int, Pair<String, List<Dia>>> = mapOf(
        2 to ("FULL_BODY" to listOf(Dia("Full Body A", FULL), Dia("Full Body B", FULL))),
        3 to ("FULL_BODY" to listOf(Dia("Full Body A", FULL), Dia("Full Body B", FULL), Dia("Full Body C", FULL))),
        4 to ("UPPER_LOWER" to listOf(
            Dia("Upper A", UPPER), Dia("Lower A", LOWER), Dia("Upper B", UPPER), Dia("Lower B", LOWER),
        )),
        5 to ("UL_PPL" to listOf(
            Dia("Upper", UPPER), Dia("Lower", LOWER), Dia("Push", PUSH), Dia("Pull", PULL), Dia("Legs", LOWER),
        )),
        6 to ("PPL" to listOf(
            Dia("Push A", PUSH), Dia("Pull A", PULL), Dia("Legs A", LOWER),
            Dia("Push B", PUSH), Dia("Pull B", PULL), Dia("Legs B", LOWER),
        )),
    )

    /** Nome da divisão + a lista de dias com seus músculos. `dias` fora de 2..6 falha alto. */
    fun divisao(dias: Int): Pair<String, List<Dia>> = DIVISOES[dias]
        ?: throw IllegalArgumentException(
            "motor v3 só cobre divisões de 2 a 6 dias por semana (especificação, " +
                "\"A divisão sai do número de dias\"); recebido: $dias"
        )

    /** Piso de séries abaixo do qual um músculo sobe para o piso do isolamento (2). */
    private const val PISO_ISOLAMENTO = 2

    /**
     * Espalha `freq` dias dentro de `diasM`, com `desloc` (posição do músculo no enum, para não
     * empilhar todo mundo nos mesmos dias). Se `freq` já cobre todos os dias, devolve todos.
     */
    fun espalhar(diasM: List<Int>, freq: Int, desloc: Int): List<Int> {
        if (freq >= diasM.size) return diasM
        val passo = diasM.size.toDouble() / freq
        return (0 until freq)
            .map { i -> diasM[(i * passo + desloc).toInt() % diasM.size] }
            .toSortedSet()
            .toList()
    }

    /**
     * Distribui `alocado` (saída de `PriorityAllocation.alocar()`) pelos dias que treinam cada
     * músculo. Frequência efetiva: não fatia composto abaixo do piso do papel — um músculo com
     * poucas séries na semana treina em menos dias, não em todos. Conta disparos
     * (`sobe_ao_piso_do_isolamento`, `limite_11_por_sessao`) — Fatia 7's padrão, regra sem
     * disparo não está provada.
     *
     * Processa `alocado` como ele vem, mesmo que `soma(alocado) > capacidade` (achado da
     * Fatia 3, 21,4% dos perfis da matriz) — não corta nada aqui. Quem corta é o laço de corte
     * por tempo, Fatia 8.
     */
    fun distribuirPelosDias(
        alocado: Map<TargetMuscle, Int>,
        dias: List<Dia>,
        disparos: MutableMap<String, Int> = mutableMapOf(),
    ): List<Map<TargetMuscle, Int>> {
        val seriesDia = List(dias.size) { mutableMapOf<TargetMuscle, Int>() }
        for ((musculo, totalOriginal) in alocado) {
            val diasM = dias.indices.filter { musculo in dias[it].musculos }
            if (diasM.isEmpty()) continue

            // `[INV]` (Fatia 8, 2026-10-27): aqui já não se sobe nada. Subir o total nesta etapa
            // era aumentar volume DEPOIS de o orçamento fechar, e era a causa de
            // `entregue > capacidade` em 167/720 perfis. A decisão migrou inteira para a etapa
            // [3] ([PriorityAllocation.pisoPrescritivel]), que financia o piso dentro do
            // orçamento ou zera o músculo. O que era mutação virou armadilha: se um total abaixo
            // do piso chegar até aqui, a etapa [3] deixou passar e o programa está errado.
            val total = totalOriginal
            require(total >= PISO_ISOLAMENTO) {
                "[INV] $musculo chegou à etapa [4] com $total série(s), abaixo do piso " +
                    "prescritível ($PISO_ISOLAMENTO) — a etapa [3] deveria ter subido ou zerado"
            }

            var freq = maxOf(1, minOf(diasM.size, total / PriorityAllocation.pisoDoPapel(musculo)))
            while (freq < diasM.size && ceilDiv(total, freq) > CapacityModel.MAX_SERIES_POR_MUSCULO_POR_SESSAO) {
                freq += 1
            }

            val escolhidos = espalhar(diasM, freq, musculo.ordinal)
            val base = total / freq
            val resto = total % freq
            for ((k, d) in escolhidos.withIndex()) {
                var s = base + if (k < resto) 1 else 0
                if (s > CapacityModel.MAX_SERIES_POR_MUSCULO_POR_SESSAO) {
                    disparos["limite_11_por_sessao"] = (disparos["limite_11_por_sessao"] ?: 0) + 1
                    s = CapacityModel.MAX_SERIES_POR_MUSCULO_POR_SESSAO
                }
                seriesDia[d][musculo] = s
            }
        }
        return seriesDia
    }

    private fun ceilDiv(a: Int, b: Int): Int = (a + b - 1) / b
}
