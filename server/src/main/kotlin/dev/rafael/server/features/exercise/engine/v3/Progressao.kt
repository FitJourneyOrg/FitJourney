package dev.rafael.server.features.exercise.engine.v3

import dev.rafael.contract.profile.Goal
import dev.rafael.server.features.exercise.engine.v3.SessionComposition.Papel
import kotlin.math.ceil
import kotlin.math.floor

/**
 * Prescrição por papel (spec, "Prescrição de cada série"): faixa de repetições, descanso e RIR alvo.
 * Espelha `spec_v3.py::PRESCRICAO`. `Papel.COMPOSTO` é o "composto pesado" da tabela e
 * `Papel.ACESSORIO` o "composto acessório". **Perder gordura é idêntico a ganhar massa** (item 18).
 */
object PrescricaoV3 {
    data class Faixa(val repsMin: Int, val repsMax: Int, val descansoSegundos: Int, val rirAlvo: Int)

    fun de(objetivo: Goal, papel: Papel): Faixa = when (objetivo) {
        Goal.GAIN_MUSCLE, Goal.LOSE_FAT -> when (papel) {
            Papel.COMPOSTO -> Faixa(6, 9, 120, 2)
            Papel.ACESSORIO -> Faixa(8, 12, 90, 1)
            Papel.ISOLAMENTO -> Faixa(12, 16, 60, 1)
        }
        Goal.MAINTAIN -> when (papel) {
            Papel.COMPOSTO -> Faixa(6, 9, 120, 2)
            Papel.ACESSORIO -> Faixa(8, 12, 90, 2)
            Papel.ISOLAMENTO -> Faixa(12, 16, 60, 2)
        }
        Goal.GENERAL_HEALTH -> when (papel) {
            Papel.COMPOSTO -> Faixa(8, 12, 120, 3)
            Papel.ACESSORIO -> Faixa(10, 14, 90, 3)
            Papel.ISOLAMENTO -> Faixa(12, 18, 75, 3)
        }
    }
}

/**
 * Motor v3, Fatia 10 — PROGRESSÃO DUPLA, série de CALIBRAÇÃO e 1RM estimado. Núcleo puro: tudo é
 * função de (estado, entrada) → (estado novo, decisão), sem relógio, sem catálogo e sem banco.
 * Espelha `server/src/test/oracle/progressao_v3.py` termo a termo; a prova é
 * `ProgressaoContraOOraculoTest`, que reproduz as ~10 mil linhas de `referencia_progressao.txt`.
 *
 * O [Estado] é **por exercício, não por vaga** (checklist da spec): a vaga pode mudar de exercício
 * num programa novo, a história da carga acompanha o exercício. A persistência é a interface
 * [dev.rafael.server.features.exercise.db.ProgressaoRepository] — sem implementação nesta fatia (schema novo,
 * espera o ok do Rafael).
 *
 * Decisões do Rafael (2026-10-28): redução de **4% fixo** por ponto de RIR abaixo do alvo (a faixa
 * de 3–5% do corpo da spec é da literatura; a contradição com o checklist foi fechada por decisão,
 * não reabrir). `[PROPOSTA]`s (a spec não decide) estão marcadas onde aparecem.
 */
object Progressao {

    enum class Decisao { SOBE, MANTEM, DESCE }

    /**
     * `rir == null` = RIR NÃO informado. **`[REGRA]` (D-3, 2026-10-28): RIR nulo aplica SÓ as regras
     * por repetição** — sem a exigência de RIR no topo, sem salto proporcional, sem tolerância
     * pós-salto, sem a redução de 4% e sem 1RM. Nada é inferido: a regra que precisa do RIR não age.
     */
    data class Serie(val reps: Int, val rir: Int?)

    /**
     * Progressão de UM exercício. `carga == null` = calibração pendente (nunca fez, ou recalibra
     * depois de uma pausa longa). `exposicoes` conta sessões; a calibração é a 1ª.
     */
    data class Estado(
        val carga: Double? = null,
        val exposicoes: Int = 0,
        /** Regra 3: a tolerância pós-salto está ativa. */
        val posSalto: Boolean = false,
        /** 1RM estimado da última sessão; `null` em isolamento ou sem dado. */
        val umRm: Double? = null,
        /** Sessões consecutivas sem [Decisao.SOBE]. */
        val semSubir: Int = 0,
    )

    data class PassoCalibracao(val carga: Double, val terminou: Boolean)

    // Espelham `spec_v3.py` (seção "Fatia 10").
    const val REDUCAO_POR_PONTO_RIR = 0.04
    const val EXPOSICOES_COM_SALTO_MAIOR = 3
    const val CALIBRACAO_RIR_MINIMO = 4
    const val CALIBRACAO_MAX_SERIES = 3
    const val CALIBRACAO_TETO_BARRA_LIVRE = 0.25
    private const val EPS = 1e-9

    /** (excesso mínimo, excesso máximo, salto): 1–2 acima do alvo → +10%, 3–4 → +25%, 5+ → +50% (spec l.80). */
    private val SALTOS = listOf(Triple(1, 2, 0.10), Triple(3, 4, 0.25), Triple(5, Int.MAX_VALUE, 0.50))

    private val COMPOSTOS = setOf(Papel.COMPOSTO, Papel.ACESSORIO)

    // ------------------------------------------------------------------------- arredondamento

    /**
     * Múltiplo de [incremento] mais próximo de [x]; **empate desce** (a carga menor é a segura).
     * Fixado porque Kotlin e Python divergem em empates exatos de `round`; o `- EPS` faz o empate de
     * ponto flutuante cair do lado certo. Espelha `para_incremento()`.
     */
    fun paraIncremento(x: Double, incremento: Double): Double {
        require(incremento > 0) { "incremento deve ser positivo" }
        return ceil(x / incremento - 0.5 - EPS) * incremento
    }

    /** Meio pra cima — o mesmo critério do `roundToInt` do resto do motor. Espelha `arredondar()`. */
    fun arredondar(x: Double): Int = floor(x + 0.5).toInt()

    // ------------------------------------------------------------------------- 1RM

    /**
     * Epley (1985) com o RIR somado às repetições: `1RM = carga × (1 + (reps + RIR) / 30)`.
     * `[PROPOSTA]`: a spec manda estimar "a partir do RIR relatado" e não escolhe a fórmula. Só é
     * confiável perto de 10 repetições — por isso o 1RM nunca ESCOLHE a carga (quem escolhe é a
     * progressão dupla), só serve de sinal para a promoção (Fatia 11).
     */
    fun umRmEpley(carga: Double, reps: Int, rir: Int): Double = carga * (1.0 + (reps + rir) / 30.0)

    /** Melhor série da sessão. **Isolamento não usa 1RM estimado** (spec, regra 5): só progressão dupla. */
    fun estimarUmRm(carga: Double, series: List<Serie>, papel: Papel): Double? {
        if (papel !in COMPOSTOS || series.isEmpty()) return null
        val comRir = series.filter { it.rir != null }
        if (comRir.isEmpty()) return null
        return comRir.maxOf { umRmEpley(carga, it.reps, it.rir!!) }
    }

    // ------------------------------------------------------------------------- calibração

    /** Salto proporcional pelo quanto o RIR informado passa do alvo. `0.0` = sem salto. Teto de +25% em barra livre. */
    fun saltoDaFaixa(excessoRir: Int, barraLivre: Boolean): Double {
        for ((de, ate, salto) in SALTOS) {
            if (excessoRir in de..ate) {
                return if (barraLivre) minOf(salto, CALIBRACAO_TETO_BARRA_LIVRE) else salto
            }
        }
        return 0.0
    }

    /** Nunca menos que UM incremento acima: um salto que arredonda para a mesma carga travaria a calibração. */
    fun cargaAposSalto(carga: Double, pct: Double, incremento: Double): Double =
        maxOf(paraIncremento(carga * (1.0 + pct), incremento), carga + incremento)

    /**
     * Uma série de calibração (`serieN` é 1-based). RIR informado ≥ 4 sobe NA HORA e a pessoa faz outra
     * série, até 3 (spec l.600). Abaixo de 4 a carga é a certa: termina sem mexer. Na 3ª série a carga
     * ainda sobe (é a sugestão para a próxima exposição) e termina.
     */
    fun passoCalibracao(
        carga: Double, rirInformado: Int?, serieN: Int, rirAlvo: Int, barraLivre: Boolean, incremento: Double,
    ): PassoCalibracao {
        // RIR nulo: a calibração é feita de RIR, então não age (D-3)
        if (rirInformado == null || rirInformado < CALIBRACAO_RIR_MINIMO) return PassoCalibracao(carga, true)
        val pct = saltoDaFaixa(rirInformado - rirAlvo, barraLivre)
        if (pct == 0.0) return PassoCalibracao(carga, true)
        val nova = cargaAposSalto(carga, pct, incremento)
        return PassoCalibracao(nova, serieN >= CALIBRACAO_MAX_SERIES)
    }

    /** Conveniência: roda os passos e devolve o [Estado] depois da 1ª exposição. Lista vazia falha alto. */
    fun calibrar(
        cargaInicial: Double, rirsInformados: List<Int?>, rirAlvo: Int, barraLivre: Boolean, incremento: Double,
    ): Estado {
        require(rirsInformados.isNotEmpty()) { "calibração sem nenhum RIR informado" }
        var carga = cargaInicial
        for ((i, rir) in rirsInformados.withIndex()) {
            val passo = passoCalibracao(carga, rir, i + 1, rirAlvo, barraLivre, incremento)
            carga = passo.carga
            if (passo.terminou) break
        }
        return Estado(carga = carga, exposicoes = 1)
    }

    // ------------------------------------------------------------------------- progressão dupla

    /**
     * Decisão ENTRE sessões. Regra 1: sobe só quando TODAS as séries batem o topo da faixa sem ficar
     * mais perto da falha que o alvo. Regra 2: o aumento é o MENOR incremento — exceto nas três
     * primeiras exposições, em que o salto proporcional da calibração também vale (pelo pior RIR da
     * sessão). Regra 3: logo depois de uma subida, repetições abaixo da faixa são aceitas enquanto o
     * RIR estiver no alvo (sem isso, o halter de 8 → 10 kg entra em loop de subir e devolver).
     * `[PROPOSTA]` (o "devolver" implícito): repetições abaixo da faixa FORA da tolerância devolvem
     * UM incremento. A regra 4 é [reducaoIntrasessao]. Espelha `decidir()`.
     */
    fun decidir(
        estado: Estado, series: List<Serie>, papel: Papel, objetivo: Goal, incremento: Double, barraLivre: Boolean,
    ): Pair<Estado, Decisao> {
        val carga = requireNotNull(estado.carga) { "sem carga: a calibração vem antes da progressão" }
        require(series.isNotEmpty()) { "sessão sem séries" }
        val faixa = PrescricaoV3.de(objetivo, papel)
        val topo = series.all { it.reps >= faixa.repsMax && (it.rir == null || it.rir >= faixa.rirAlvo) }
        val abaixo = series.any { it.reps < faixa.repsMin }
        val todosComRir = series.all { it.rir != null }
        // RIR nulo: a tolerância pós-salto NÃO vale (não há como confirmar o RIR no alvo)
        val rirNoAlvo = todosComRir && series.all { it.rir!! >= faixa.rirAlvo }
        val exposicoes = estado.exposicoes + 1
        // sessão sem nenhum RIR não apaga o 1RM histórico
        val umRm = estimarUmRm(carga, series, papel) ?: estado.umRm.takeIf { series.none { it.rir != null } }

        if (topo) {
            val pct = if (exposicoes <= EXPOSICOES_COM_SALTO_MAIOR && todosComRir) {
                saltoDaFaixa(series.minOf { it.rir!! } - faixa.rirAlvo, barraLivre)
            } else {
                0.0
            }
            val nova = if (pct != 0.0) cargaAposSalto(carga, pct, incremento) else carga + incremento
            return Estado(nova, exposicoes, true, umRm, 0) to Decisao.SOBE
        }
        if (abaixo && !(estado.posSalto && rirNoAlvo)) {
            val nova = maxOf(carga - incremento, incremento)
            return Estado(nova, exposicoes, false, umRm, estado.semSubir + 1) to Decisao.DESCE
        }
        // Fica. A tolerância só continua se foi ela que segurou a carga; entrar na faixa a encerra.
        return Estado(carga, exposicoes, abaixo && estado.posSalto, umRm, estado.semSubir + 1) to Decisao.MANTEM
    }

    /**
     * Regra 4: se a 1ª série do dia saiu mais difícil que o alvo, as seguintes reduzem **4% por ponto**
     * de RIR abaixo do alvo (linear sobre a carga do dia). Devolve (carga das próximas séries,
     * `anulada`). `anulada` = a redução arredondou para a mesma carga (4% de 8 kg num incremento de
     * 2 kg): a carga fica e o chamador sabe que a regra não agiu — não existe carga menor carregável.
     */
    fun reducaoIntrasessao(
        carga: Double, rirPrimeiraSerie: Int?, rirAlvo: Int, incremento: Double,
    ): Pair<Double, Boolean> {
        // RIR nulo: a regra 4 não age e NÃO é "anulada" (não havia o que medir)
        if (rirPrimeiraSerie == null) return carga to false
        val falta = rirAlvo - rirPrimeiraSerie
        if (falta <= 0) return carga to false
        val nova = maxOf(paraIncremento(carga * (1.0 - REDUCAO_POR_PONTO_RIR * falta), incremento), incremento)
        if (nova >= carga - EPS) return carga to true
        return nova to false
    }
}
