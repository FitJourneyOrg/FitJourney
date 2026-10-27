package dev.rafael.server.features.exercise.engine.v3

import dev.rafael.contract.profile.Goal
import dev.rafael.contract.profile.Level
import dev.rafael.server.features.exercise.engine.TargetMuscle

/**
 * Motor v3, etapa [1] — ALVOS (`docs/trabalho/motor-v3-desenho-e-fatias.md` §2.1, Fatia 1).
 *
 * Separado de `VolumeModel.kt` (motor antigo) por decisão do Rafael (`trabalho/motor-v3-fatia-1.md`):
 * o motor antigo não tem teto de sessão nem alocação, então não pode receber estes números —
 * sessões cresceriam sem nada que as limite. Esta tabela só passa a valer quando a Fatia 3
 * trocar o `StructureEngine` pelo pipeline novo.
 *
 * Números da especificação (`propostas/motor-v3-ESPECIFICACAO.md`, "Volume semanal por músculo"
 * e "Volume indireto"). `TargetMuscle` é reaproveitado do motor antigo: é só o conjunto de
 * músculos finos, não muda entre as duas versões.
 */

/** Tabela de volume semanal ratificada para o motor v3. Séries efetivas por semana. */
object VolumeTargets {
    private data class V(val ini: Int, val inter: Int, val adv: Int, val mrv: Int?)

    private val table = mapOf(
        TargetMuscle.CHEST to V(8, 14, 18, 22),
        TargetMuscle.BACK to V(10, 16, 20, 25),
        TargetMuscle.SHOULDERS to V(6, 12, 16, 22),
        // Braço subiu (seu item 20): bíceps e tríceps a 12 no intermediário, não ~3.
        TargetMuscle.BICEPS to V(6, 12, 15, 18),
        TargetMuscle.TRICEPS to V(7, 12, 16, 20),
        TargetMuscle.FOREARMS to V(3, 6, 9, 12),
        TargetMuscle.QUADS to V(6, 12, 16, 20),
        TargetMuscle.POSTERIOR to V(6, 10, 14, 18),
        TargetMuscle.CALVES to V(5, 9, 12, 16),
        // Glúteo 4/8/12 → **8/10/12** (2026-10-27). Mesma construção do *item 20* nos braços:
        // alvo = indireto recebido + direto desejado. Medido, o glúteo recebe indireto **6/7/8**
        // — quase plano, porque depende de quanto composto de quadríceps/posterior entra, não do
        // nível —, então 8/10/12 entrega direto **2/3/4** e o ADVANCED fica **intocado** em 12.
        //
        // **Natureza do motivo: é decisão de PRODUTO, não inferência da literatura.** A isenção
        // do iniciante no híbrido (no BEGINNER o indireto conta para o MEV) continua valendo como
        // **regra geral**, sustentada por Iversen et al. 2021 e Gentil — essas fontes embasam a
        // regra geral e **não** esta exceção. O glúteo é **exceção declarada** neste app porque é
        // prioridade de mercado e zero exercício direto lê como programa incompleto.
        //
        // Descartado: 8/9/10 (o mínimo aritmético para cruzar o piso em cada nível) — derrubava o
        // ADVANCED de 12 para 10, contra a premissa de volume crescente com o nível.
        TargetMuscle.GLUTES to V(8, 10, 12, 16),
    )

    /**
     * Fator de objetivo sobre o ALVO SEMANAL (especificação, "Como as outras variáveis modulam"):
     * ganhar 1,0 · perder 1,0 · **manter 0,6** · saúde geral usa o piso. Espelha
     * `spec_v3.py::FATOR_OBJETIVO_ALVO`.
     *
     * **Não confundir com [CapacityModel.fatorObjetivo]**, que é o mesmo 0,6 aplicado ao teto da
     * SESSÃO. A especificação manda aplicar nos dois ("esses fatores agora multiplicam também o
     * teto da sessão, não só o alvo semanal") — e até 2026-10-27 este aqui faltava, o que dava a
     * todo perfil `MAINTAIN` um alvo 67% maior que o do oráculo, em 180 dos 720 perfis. Defeito
     * registrado como E10 em `docs/trabalho/motor-v3-fatia-8.md`.
     */
    fun fatorObjetivoAlvo(goal: Goal): Double = if (goal == Goal.MAINTAIN) 0.6 else 1.0

    /**
     * Alvo semanal por músculo/nível/objetivo. GENERAL_HEALTH usa o piso (MEV); os demais usam a
     * coluna do nível vezes [fatorObjetivoAlvo], limitada ao MRV. Espelha `motor_v3.py::alvos()`
     * termo a termo: `min(coluna × fator, teto)`.
     */
    fun weeklySets(muscle: TargetMuscle, level: Level, goal: Goal): Double {
        val v = table.getValue(muscle)
        if (goal == Goal.GENERAL_HEALTH) return v.ini.toDouble()
        val coluna = when (level) {
            Level.BEGINNER -> v.ini
            Level.INTERMEDIATE -> v.inter
            Level.ADVANCED -> v.adv
        }
        val alvo = coluna * fatorObjetivoAlvo(goal)
        return if (v.mrv != null) minOf(alvo, v.mrv.toDouble()) else alvo
    }

    /** Teto (MRV). `null` = sem teto definido ainda (abdômen, até o catálogo ter exercícios). */
    fun mrv(muscle: TargetMuscle): Int? = table.getValue(muscle).mrv

    /**
     * Piso (coluna do iniciante), independente do nível/objetivo do usuário — é o piso que
     * a alocação sob escassez usa (Fatia 3): `min(direta, max(0, piso − indireto))`.
     */
    fun piso(muscle: TargetMuscle): Int = table.getValue(muscle).ini

    /**
     * Volume de MANUTENÇÃO (MV): o mínimo para **não perder**, que é menor que o MEV — o mínimo
     * para **crescer** ([piso], a coluna do iniciante). São grandezas distintas na literatura (a
     * RP publica as duas: bíceps MV ≈ 4 contra MEV ≈ 8) e a nossa tabela só tinha uma.
     *
     * `[PROPOSTA]` 2026-10-27: o fator é **derivado**, não tabela ratificada — reaproveita o
     * fator de "manter", que já passou por revisão, aplicado ao piso. A derivação fecha com a
     * decisão já tomada: para o iniciante, o alvo de `MAINTAIN` (0,6 × coluna do iniciante) É
     * exatamente o MV, então "manter" passa a significar "treinar no volume de manutenção".
     * Trocar por uma tabela revisada é trocar esta função.
     */
    fun minimoEfetivo(muscle: TargetMuscle, goal: Goal): Double =
        piso(muscle) * fatorObjetivoAlvo(goal)
}

/**
 * Volume indireto: o motor conta meia série para o sinergista a cada série de composto do
 * doador. Ombro não entra como doador (o alvo "ombro" é o deltoide inteiro, descontar
 * subserviria lateral/posterior).
 *
 * **`[INV]` (2026-10-27): nenhum coeficiente aqui sem fonte registrada.** Três pares foram
 * **removidos** nesta rodada por não ter nenhuma — `costas → antebraço` 0,5,
 * `posterior → antebraço` 0,5 e `glúteo → posterior` 0,2. Eram eles que causavam o
 * double-counting do antebraço (recebia 6/7/12 contra alvo 3/6/9, ficando com **zero** trabalho
 * direto em todo nível) e o do posterior. Ver D-1/D-2 em
 * `docs/propostas/volume-efetivo-PROPOSTA.md`.
 *
 * Fonte de cada par sobrevivente:
 * - **peito/ombro → tríceps** — Pelland et al. 2025 (*Sports Medicine*, 67 estudos): entre
 *   contar a série indireta como 0, 0,5 ou 1, a quantificação **fracionada** foi a de evidência
 *   mais forte. Confiança **alta**.
 * - **costas → bíceps** — Pelland et al. 2025, idem; Mannarino et al. 2021 mediu rosca +11,06%
 *   contra remada +5,16% na espessura dos flexores do cotovelo, razão coerente com 0,5.
 *   Confiança **alta**.
 * - **quadríceps → glúteo** — Kubo et al. 2019: glúteo +6,7% só com agachamento. Confiança
 *   **alta**.
 * - **posterior → glúteo** — **extensão por analogia** do par irmão acima: a dobradiça também é
 *   extensão de quadril (Kubo 2019). Confiança **média**. Zerá-lo contradiria a biomecânica que
 *   sustenta o par do agachamento.
 *
 * Ressalvas que valem para todos: 0,5 é o melhor de **três valores testados**, não estimativa
 * contínua, e os estudos são majoritariamente de não treinados jovens do sexo masculino. É
 * heurística calibrável, não verdade.
 */
object VolumeIndirectTable {
    /** doador -> (recipiente -> coeficiente). */
    private val coefficients: Map<TargetMuscle, Map<TargetMuscle, Double>> = mapOf(
        TargetMuscle.CHEST to mapOf(TargetMuscle.TRICEPS to 0.5),
        TargetMuscle.SHOULDERS to mapOf(TargetMuscle.TRICEPS to 0.5),
        TargetMuscle.BACK to mapOf(TargetMuscle.BICEPS to 0.5),
        TargetMuscle.POSTERIOR to mapOf(TargetMuscle.GLUTES to 0.5),
        TargetMuscle.QUADS to mapOf(TargetMuscle.GLUTES to 0.5),
    )

    /**
     * Indireto recebido por músculo, a partir das séries de composto por semana de cada doador.
     * Músculo sem crédito nenhum (ex.: ombro, que nunca é recipiente) não aparece no mapa de
     * saída — ler com `?: 0.0`.
     */
    fun indiretoRecebido(compostoPorMusculo: Map<TargetMuscle, Double>): Map<TargetMuscle, Double> {
        val resultado = mutableMapOf<TargetMuscle, Double>()
        for ((doador, porSets) in compostoPorMusculo) {
            val creditos = coefficients[doador] ?: continue
            for ((recipiente, coeficiente) in creditos) {
                resultado[recipiente] = (resultado[recipiente] ?: 0.0) + porSets * coeficiente
            }
        }
        return resultado
    }
}

/** Demanda direta: o que falta além do que o indireto já cobre. Nunca negativa. */
fun demandaDireta(alvo: Double, indireto: Double): Double = maxOf(0.0, alvo - indireto)

/**
 * Conveniência: aplica `demandaDireta` a todos os músculos de `alvo`, usando o indireto
 * calculado a partir de `compostoPorMusculo` (séries de composto por semana de cada doador —
 * essa conta é da Fatia 5; aqui ela só é consumida, não produzida).
 */
fun demandaDiretaPorMusculo(
    alvo: Map<TargetMuscle, Double>,
    compostoPorMusculo: Map<TargetMuscle, Double>,
): Map<TargetMuscle, Double> {
    val indireto = VolumeIndirectTable.indiretoRecebido(compostoPorMusculo)
    return alvo.mapValues { (musculo, alvoDoMusculo) ->
        demandaDireta(alvoDoMusculo, indireto[musculo] ?: 0.0)
    }
}
