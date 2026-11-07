package dev.rafael.server.features.exercise.engine.v3

/**
 * Progressão por NÍVEL — fatia "progressao-por-nivel" (2026-11-06,
 * `docs/trabalho/progressao-por-nivel-plano.md`). Paralelo a [ProgressaoPorRepeticao] e a
 * [Progressao], não emenda de nenhuma das duas: `BAND`/`HAND_GRIP` (`incremento == null`, não
 * `BODYWEIGHT`) não têm carga numérica nem fazem sentido numa faixa de reps que sobe/desce — o
 * elástico/aparelho é que troca de resistência, em passos discretos.
 *
 * Decisão do Rafael (2026-11-06): **nível RELATIVO, sem modelar o kit do usuário**. `nivelAtual`
 * não tem significado absoluto nenhum (não é "elástico vermelho" nem newton nenhum) — só conta
 * quantas trocas de resistência a pessoa já fez pra cima/baixo desde que começou o exercício. O
 * app nunca pergunta nem sabe qual elástico é; só fala "sobe"/"desce um nível".
 *
 * Diferença-chave pro eixo de repetição: ali a FAIXA de reps é que sobe/desce; aqui a faixa
 * (`PrescricaoV3.Faixa`, fixa por papel/objetivo) fica FIXA a vida toda — é o `nivelAtual` que
 * muda. Mais fiel à prática real: a pessoa continua treinando no mesmo range de reps, só troca de
 * elástico quando bate o topo ou cai abaixo do piso dele.
 *
 * Núcleo puro: mesma forma de [ProgressaoPorRepeticao] — (estado, entrada) → (estado novo,
 * decisão), sem relógio, sem catálogo e sem banco.
 */
object ProgressaoPorNivel {

    enum class Decisao { SOBE, MANTEM, DESCE }

    /** Mesmo formato dos outros dois motores — `rir == null` não bloqueia o topo (ver [decidir]). */
    data class Serie(val reps: Int, val rir: Int?)

    /**
     * `nivelAtual` começa em **1** na 1ª exposição — sem calibração, mesmo motivo do eixo de
     * reps: sem carga nem faixa pra medir excesso de RIR, a 1ª sessão já é dado válido. Nunca
     * desce abaixo de 1 (não existe "nível 0" nem negativo).
     */
    data class Estado(
        val nivelAtual: Int = 1,
        val exposicoes: Int = 0,
        /** Sessões consecutivas sem [Decisao.SOBE] — mesmo papel do `semSubir` dos outros dois motores. */
        val semSubir: Int = 0,
    )

    /**
     * Decisão ENTRE sessões: sobe de nível só quando TODAS as séries batem o topo da faixa FIXA
     * (`faixaOriginal.repsMax`) sem ficar mais perto da falha que o alvo (RIR nulo não bloqueia —
     * mesma leitura do D-3, igual ao eixo de reps). Desce de nível quando alguma série cai abaixo
     * do piso da faixa (`faixaOriginal.repsMin`), nunca abaixo do nível 1.
     */
    fun decidir(
        estado: Estado,
        series: List<Serie>,
        faixaOriginal: PrescricaoV3.Faixa,
    ): Pair<Estado, Decisao> {
        require(series.isNotEmpty()) { "sessão sem séries" }
        val exposicoes = estado.exposicoes + 1
        val topo = series.all { it.reps >= faixaOriginal.repsMax && (it.rir == null || it.rir >= faixaOriginal.rirAlvo) }
        val abaixo = series.any { it.reps < faixaOriginal.repsMin }

        if (topo) {
            return Estado(estado.nivelAtual + 1, exposicoes, 0) to Decisao.SOBE
        }
        if (abaixo) {
            val novoNivel = maxOf(estado.nivelAtual - 1, 1)
            return Estado(novoNivel, exposicoes, estado.semSubir + 1) to Decisao.DESCE
        }
        return estado.copy(exposicoes = exposicoes, semSubir = estado.semSubir + 1) to Decisao.MANTEM
    }
}
