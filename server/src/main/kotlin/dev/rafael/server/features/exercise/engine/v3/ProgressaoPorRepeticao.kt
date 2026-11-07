package dev.rafael.server.features.exercise.engine.v3

import dev.rafael.contract.workout.Papel

/**
 * Progressão por REPETIÇÃO — fatia "progressao-por-repeticao" (2026-11-06,
 * `docs/trabalho/progressao-por-repeticao-plano.md`). Paralelo a [Progressao], não uma emenda
 * dela: exercícios sem `incremento` (hoje só `BODYWEIGHT` puro, 26 do catálogo — `BAND`/
 * `HAND_GRIP` ficam de fora, débito separado) não têm carga numérica pra progredir, então o
 * sinal de esforço vira a própria FAIXA de repetições, não o peso.
 *
 * Núcleo puro: tudo é função de (estado, entrada) → (estado novo, decisão), sem relógio, sem
 * catálogo e sem banco — mesmo contrato do [Progressao]. O chamador ([Progressao] não muda de
 * assinatura) escolhe qual motor invocar por `incremento == null`.
 */
object ProgressaoPorRepeticao {

    enum class Decisao { SOBE, MANTEM, DESCE }

    /** Mesmo formato de [Progressao.Serie] — `rir == null` não bloqueia o topo (ver [decidir]). */
    data class Serie(val reps: Int, val rir: Int?)

    /**
     * Faixa de reps ATUAL da pessoa pro exercício — diferente da [PrescricaoV3.Faixa] (que é fixa
     * por papel/objetivo): aqui `min`/`max` sobem e descem com o desempenho, com piso na faixa
     * original. Estado inicial (1ª exposição) é a faixa original — não existe calibração por reps,
     * só por carga: sem carga pra medir RIR-sob-carga, a 1ª sessão já é dado válido.
     */
    data class Estado(
        val repsMin: Int,
        val repsMax: Int,
        val exposicoes: Int = 0,
        /** Sessões consecutivas sem [Decisao.SOBE] — mesmo papel do `semSubir` de [Progressao.Estado]. */
        val semSubir: Int = 0,
        /**
         * Sinal de força comparável entre mesociclos, pras 4 âncoras do avançado que são
         * `BODYWEIGHT` (fatia "ancoras-avancado-bodyweight", 2026-11-06) — ver [estimarSinalDeForca].
         * Mesmo papel do `Progressao.Estado.umRm`: carrega entre sessões, cai no valor anterior
         * quando a sessão não tem RIR pra recalcular.
         */
        val sinalForca: Double? = null,
    )

    /** Mesmo conjunto de [Progressao] (`COMPOSTOS`) — isolamento não entra na comparação de avançado. */
    private val COMPOSTOS = setOf(Papel.COMPOSTO, Papel.ACESSORIO)

    /**
     * Sinal de força comparável ENTRE MESOCICLOS do MESMO exercício — não é 1RM em kg (não há
     * carga pra multiplicar, [Progressao.umRmEpley] não se aplica), é só `reps + RIR` da melhor
     * série, crescente com desempenho. Existe só pra alimentar
     * `PromocaoDeNivel.diagnosticoAvancado` nas 4 âncoras do avançado que são `BODYWEIGHT`
     * (fatia "ancoras-avancado-bodyweight", 2026-11-06, `docs/fontes/debitos.md`) — sem ele essas
     * âncoras caíam permanentemente em DADO INSUFICIENTE, mesmo pra quem treina em academia.
     * Mesma restrição do `estimarUmRm`: só COMPOSTO/ACESSORIO (isolamento não compara 1RM) e só
     * séries com RIR informado.
     */
    fun estimarSinalDeForca(series: List<Serie>, papel: Papel): Double? {
        if (papel !in COMPOSTOS || series.isEmpty()) return null
        val comRir = series.filter { it.rir != null }
        if (comRir.isEmpty()) return null
        return comRir.maxOf { (it.reps + it.rir!!).toDouble() }
    }

    /**
     * Teto absoluto, independente de papel (decisão do Rafael, 2026-11-06): isolamento já nasce em
     * 12–16, composto em 6–9/8–12 — um teto único por cima de todos cobre a faixa real sem tabela
     * nova. Acima disso o sinal de hipertrofia/força vira resistência muscular; não tem mais sentido
     * subir. Ao bater: [Decisao.MANTEM], sem alarme — sugerir variação mais difícil do exercício é
     * débito registrado (`docs/fontes/debitos.md`), não resolvido aqui.
     */
    const val TETO_REPETICOES = 20

    /**
     * Decisão ENTRE sessões, espelhando as regras 1/2 de [Progressao.decidir] mas no eixo de
     * repetição: sobe só quando TODAS as séries batem o topo da faixa ATUAL sem ficar mais perto da
     * falha que o alvo (RIR nulo não bloqueia — mesma leitura do D-3: a regra que precisa do RIR
     * simplesmente não age). O aumento é sempre +1 rep nos dois limites, piso e teto únicos (não há
     * salto proporcional nem calibração: não existe "primeiras exposições com salto maior" sem
     * carga pra medir o excesso). Descida simétrica, nunca abaixo da [faixaOriginal] — essa é a
     * prescrição mínima pro papel/objetivo da pessoa, não dá pra cair mais que isso.
     */
    fun decidir(
        estado: Estado,
        series: List<Serie>,
        faixaOriginal: PrescricaoV3.Faixa,
        papel: Papel,
    ): Pair<Estado, Decisao> {
        require(series.isNotEmpty()) { "sessão sem séries" }
        val exposicoes = estado.exposicoes + 1
        val topo = series.all { it.reps >= estado.repsMax && (it.rir == null || it.rir >= faixaOriginal.rirAlvo) }
        val abaixo = series.any { it.reps < estado.repsMin }
        // Mesmo fallback do Progressao.decidir: sem RIR na sessão não dá pra recalcular, mantém o
        // sinal anterior em vez de apagar o dado.
        val sinalForca = estimarSinalDeForca(series, papel) ?: estado.sinalForca.takeIf { series.none { it.rir != null } }

        if (topo) {
            if (estado.repsMax >= TETO_REPETICOES) {
                return estado.copy(exposicoes = exposicoes, semSubir = estado.semSubir + 1, sinalForca = sinalForca) to Decisao.MANTEM
            }
            return Estado(estado.repsMin + 1, estado.repsMax + 1, exposicoes, 0, sinalForca) to Decisao.SOBE
        }
        if (abaixo) {
            val novoMin = maxOf(estado.repsMin - 1, faixaOriginal.repsMin)
            val novoMax = maxOf(estado.repsMax - 1, faixaOriginal.repsMax)
            return Estado(novoMin, novoMax, exposicoes, estado.semSubir + 1, sinalForca) to Decisao.DESCE
        }
        return estado.copy(exposicoes = exposicoes, semSubir = estado.semSubir + 1, sinalForca = sinalForca) to Decisao.MANTEM
    }
}
