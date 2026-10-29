package dev.rafael.server.features.exercise.engine.v3

import dev.rafael.server.features.exercise.engine.TargetMuscle

/**
 * Motor v3, etapa [5] — composição da sessão: quantos exercícios por músculo, com que papel
 * (âncora composto / acessório / isolamento) e quantas séries em cada.
 *
 * Espelha `server/src/test/oracle/motor_v3.py::compor()`/`compor_sessao()`/`papeis_para()`/
 * `distribuir()`/`isolamentos()` termo a termo (Fatia 5, `docs/trabalho/motor-v3-fatia-5.md`).
 *
 * **Decisão de escopo (2026-10-23):** não inclui o ciclo do ponto fixo do indireto. O oráculo
 * mede `compostos_semana` sobre o exercício REAL escolhido no catálogo (`v.exercicio.composto`),
 * não sobre o papel assinalado aqui — e os dois divergem em 293/720 perfis (40,7%), por causa do
 * fallback do `preencher()` quando falta candidato (vagas vazias, Fatia 6). Um ponto fixo
 * construído sobre o papel, antes do catálogo existir, estaria errado em 4 de 10 casos — por
 * isso o laço fica para a abertura da Fatia 6, que é onde "composto medido sobre o entregue"
 * (Decisão B) passa a existir de verdade.
 */
object SessionComposition {

    enum class Papel { COMPOSTO, ACESSORIO, ISOLAMENTO }

    data class Vaga(val alvo: TargetMuscle, val papel: Papel, val series: Int)

    private val SO_ISOLAMENTO = setOf(
        TargetMuscle.BICEPS, TargetMuscle.TRICEPS, TargetMuscle.FOREARMS,
        TargetMuscle.CALVES,
    )
    private const val PISO_COMPOSTO = 3
    private const val PISO_ACESSORIO = 3
    private const val PISO_ISOLAMENTO = 2
    private const val MAX_SERIES_EXERCICIO = 4
    private const val MAX_EXERCICIOS_MUSCULO = 3
    private const val OMBRO_LIMITE = 10
    private const val OMBRO_MINIMO_COMPOSTO = 3

    private fun pisoDoPapel(papel: Papel): Int = when (papel) {
        Papel.COMPOSTO -> PISO_COMPOSTO
        Papel.ACESSORIO -> PISO_ACESSORIO
        Papel.ISOLAMENTO -> PISO_ISOLAMENTO
    }

    /** Piso de cada papel, depois enche o primeiro até [MAX_SERIES_EXERCICIO] e passa ao próximo. */
    fun distribuir(total: Int, papeis: List<Papel>): List<Int> {
        val x = papeis.map { pisoDoPapel(it) }.toMutableList()
        var resto = total - x.sum()
        require(resto >= 0) { "total $total não cobre o piso dos papéis $papeis" }
        for (i in x.indices) {
            val add = minOf(MAX_SERIES_EXERCICIO - x[i], resto)
            x[i] += add
            resto -= add
        }
        require(resto == 0) {
            "sobrou $resto série(s) depois de encher $papeis a $MAX_SERIES_EXERCICIO — total " +
                "$total não é alcançável por essa combinação de papéis"
        }
        return x
    }

    fun papeisPara(musculo: TargetMuscle, series: Int): List<Papel> {
        val n = if (series <= 4) 1 else if (series <= 8) 2 else 3
        if (musculo in SO_ISOLAMENTO) return List(n) { Papel.ISOLAMENTO }
        if (n == 1 && series < PISO_COMPOSTO) return listOf(Papel.ISOLAMENTO)
        return when (n) {
            1 -> listOf(Papel.COMPOSTO)
            2 -> listOf(Papel.COMPOSTO, Papel.ISOLAMENTO)
            else -> listOf(Papel.COMPOSTO, Papel.ACESSORIO, Papel.ISOLAMENTO)
        }
    }

    fun isolamentos(musculo: TargetMuscle, series: Int, maximo: Int): List<Vaga> {
        var n = minOf(maximo, if (series <= 4) 1 else if (series <= 8) 2 else 3)
        val s = minOf(series, n * MAX_SERIES_EXERCICIO)
        while (n > 1 && s < n * PISO_ISOLAMENTO) n -= 1
        return distribuir(s, List(n) { Papel.ISOLAMENTO }).map { Vaga(musculo, Papel.ISOLAMENTO, it) }
    }

    /**
     * Compõe um músculo numa sessão. `peitoCompostoDoDia` é o total de séries de peito já
     * compostas/acessório no mesmo dia — só importa para [TargetMuscle.SHOULDERS] (regra do
     * ombro: desenvolvimento cede espaço ao composto de peito do dia).
     */
    fun compor(
        musculo: TargetMuscle,
        series: Int,
        peitoCompostoDoDia: Int,
        disparos: MutableMap<String, Int> = mutableMapOf(),
    ): List<Vaga> {
        val papeis = papeisPara(musculo, series)
        val vagas = papeis.zip(distribuir(series, papeis)).map { (p, x) -> Vaga(musculo, p, x) }
        if (musculo != TargetMuscle.SHOULDERS) return vagas

        val comp = vagas.filter { it.papel == Papel.COMPOSTO || it.papel == Papel.ACESSORIO }.sumOf { it.series }
        val limite = OMBRO_LIMITE - peitoCompostoDoDia
        if (comp <= limite) return vagas
        if (limite < OMBRO_MINIMO_COMPOSTO) {
            disparos["ombro_vira_isolamento"] = (disparos["ombro_vira_isolamento"] ?: 0) + 1
            return isolamentos(musculo, series, MAX_EXERCICIOS_MUSCULO)
        }
        disparos["ombro_teto_do_desenvolvimento"] = (disparos["ombro_teto_do_desenvolvimento"] ?: 0) + 1
        val ancoraSeries = minOf(MAX_SERIES_EXERCICIO, limite)
        val ancora = Vaga(musculo, Papel.COMPOSTO, ancoraSeries)
        return listOf(ancora) + isolamentos(musculo, series - ancoraSeries, MAX_EXERCICIOS_MUSCULO - 1)
    }

    /**
     * Compõe uma sessão inteira. A ordem é a de [TargetMuscle] (igual a `ALVOS` no oráculo) —
     * importa porque CHEST precisa vir antes de SHOULDERS para a regra do ombro ler o peito do
     * dia já composto.
     */
    fun comporSessao(
        series: Map<TargetMuscle, Int>,
        disparos: MutableMap<String, Int> = mutableMapOf(),
    ): List<Vaga> {
        val vagas = mutableListOf<Vaga>()
        var peitoCompostoDoDia = 0
        for (musculo in TargetMuscle.entries) {
            val s = series[musculo] ?: continue
            if (s <= 0) continue
            val vs = compor(musculo, s, peitoCompostoDoDia, disparos)
            if (musculo == TargetMuscle.CHEST) {
                peitoCompostoDoDia = vs.filter { it.papel == Papel.COMPOSTO || it.papel == Papel.ACESSORIO }.sumOf { it.series }
            }
            vagas += vs
        }
        return vagas
    }
}
