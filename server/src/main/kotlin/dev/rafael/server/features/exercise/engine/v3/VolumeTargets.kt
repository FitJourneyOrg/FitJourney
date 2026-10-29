package dev.rafael.server.features.exercise.engine.v3

import dev.rafael.contract.profile.Goal
import dev.rafael.contract.profile.Level
import dev.rafael.contract.profile.MuscleGroup
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
     * registrado como E10 em `docs/historico/motor-v3-fatia-8.md`.
     */
    fun fatorObjetivoAlvo(goal: Goal): Double = if (goal == Goal.MAINTAIN) 0.6 else 1.0

    /**
     * Alvo semanal por músculo/nível/objetivo. GENERAL_HEALTH usa o piso (MEV); os demais usam a
     * coluna do nível vezes [fatorObjetivoAlvo], limitada ao MRV. Espelha `motor_v3.py::alvos()`
     * termo a termo: `min(coluna × fator, teto)`, e depois o bônus de [foco] (Fatia 10), que soma
     * DEPOIS do fator de objetivo e ainda respeita o MRV. Sem foco o resultado é idêntico ao de antes.
     */
    fun weeklySets(
        muscle: TargetMuscle,
        level: Level,
        goal: Goal,
        foco: List<MuscleGroup> = emptyList(),
    ): Double {
        val v = table.getValue(muscle)
        val base = if (goal == Goal.GENERAL_HEALTH) {
            v.ini.toDouble()
        } else {
            val coluna = when (level) {
                Level.BEGINNER -> v.ini
                Level.INTERMEDIATE -> v.inter
                Level.ADVANCED -> v.adv
            }
            coluna * fatorObjetivoAlvo(goal)
        }
        val bonus = bonusDeFoco(level, goal, foco)[muscle] ?: 0
        var valor = base + bonus

        // `[REGRA]` Fatia 11: com foco ativo, todo alvo FORA do foco vai ao PISO (coluna do
        // iniciante) -- exceto BACK, isento quando a ênfase é em CHEST ou SHOULDERS (isenção
        // NOMEADA, "puxar nunca cai abaixo de empurrar" -- não é desigualdade geral: SHOULDERS
        // contém trabalho de puxar na curadoria, somá-lo como "empurrar" contaria o antídoto
        // como veneno). Sem foco, resultado idêntico ao de antes. Espelha `motor_v3.py::alvos()`.
        val focoAlvos = alvosDoFoco(level, goal, foco)
        if (focoAlvos.isNotEmpty() && muscle !in focoAlvos) {
            val isento = muscle == TargetMuscle.BACK &&
                focoEfetivo(level, goal, foco).any { it in EMPURRAR }
            if (!isento) valor = minOf(valor, piso(muscle).toDouble())
        }
        return if (v.mrv != null) minOf(valor, v.mrv.toDouble()) else valor
    }

    // ---------------------------------------------------------------------------- foco (Fatia 10)

    /** +3 para músculo pequeno (spec, tabela "Priorização: agora é uma fase"). */
    const val FOCO_BONUS_PEQUENO = 3

    /** +6 para músculo grande. */
    const val FOCO_BONUS_GRANDE = 6
    const val FOCO_MAX_GRUPOS = 2

    /**
     * `[REGRA]` 2026-10-28: "grande" é DERIVADO do [PriorityAllocation.PESO_PRIORIDADE] (fonte única, o
     * mesmo princípio da `ORDEM_SACRIFICIO`) por uma fronteira DECLARADA: peso ≥ [FOCO_FRONTEIRA_GRANDE].
     * A fronteira 0,6 fica entre TRICEPS (0,55) e GLUTES (0,7), a 0,05 e 0,10 deles: ninguém na borda.
     * Resultado: CHEST, BACK, SHOULDERS, QUADS, POSTERIOR, GLUTES grandes; BICEPS, TRICEPS, CALVES,
     * FOREARMS pequenos. NÃO é o conjunto `PROTEGIDOS_ALOCACAO`: proteção na alocação é outra pergunta.
     * Espelha `spec_v3.py::GRANDES`.
     */
    const val FOCO_FRONTEIRA_GRANDE = 0.6

    val GRANDES: Set<TargetMuscle>
        get() = TargetMuscle.entries.filter { PriorityAllocation.PESO_PRIORIDADE.getValue(it) >= FOCO_FRONTEIRA_GRANDE }.toSet()

    /**
     * Foco do quiz (`MuscleGroup`, grosso) → alvos finos. Copiado do motor antigo
     * (`VolumeModel.targetsForFocus`): o v3 não depende do v2, que vai ser substituído.
     * Espelha `spec_v3.py::FOCO_PARA_ALVOS`.
     */
    fun alvosDoFoco(grupo: MuscleGroup): Set<TargetMuscle> = when (grupo) {
        MuscleGroup.CHEST -> setOf(TargetMuscle.CHEST)
        MuscleGroup.BACK -> setOf(TargetMuscle.BACK)
        MuscleGroup.SHOULDERS -> setOf(TargetMuscle.SHOULDERS)
        MuscleGroup.BICEPS -> setOf(TargetMuscle.BICEPS)
        MuscleGroup.TRICEPS -> setOf(TargetMuscle.TRICEPS)
        MuscleGroup.FOREARMS -> setOf(TargetMuscle.FOREARMS)
        MuscleGroup.GLUTES -> setOf(TargetMuscle.GLUTES)
        MuscleGroup.LEGS -> setOf(TargetMuscle.QUADS, TargetMuscle.POSTERIOR, TargetMuscle.CALVES)
        MuscleGroup.CORE -> emptySet()   // vocabulário do usuário, sem alvo de volume (V68): foco nele não tem efeito
    }

    /** Fatia 11: os dois grupos de "empurrar" citados pela spec na isenção do BACK. Espelha `motor_v3.py::EMPURRAR`. */
    val EMPURRAR: Set<MuscleGroup> = setOf(MuscleGroup.CHEST, MuscleGroup.SHOULDERS)

    /**
     * Os [TargetMuscle] que o foco EFETIVO deste perfil cobre -- mesma expansão do bônus
     * ([bonusDeFoco]), usada também pelo piso ([weeklySets]) e pela proteção dinâmica na
     * alocação ([PriorityAllocation.alocar], `protegidosExtra`). Espelha `motor_v3.py::alvos_do_foco()`.
     */
    fun alvosDoFoco(level: Level, goal: Goal, foco: List<MuscleGroup>): Set<TargetMuscle> =
        focoEfetivo(level, goal, foco).flatMap { alvosDoFoco(it) }.toSet()

    /**
     * Foco que VALE: só INTERMEDIATE/ADVANCED e nunca GENERAL_HEALTH, no máximo 2 grupos — mesma
     * elegibilidade do motor antigo (`DeterministicWorkoutGenerator.effectiveFocus`) e da spec.
     * Fora disso o foco é ignorado, sem erro. Espelha `motor_v3.py::foco_efetivo()`.
     */
    fun focoEfetivo(level: Level, goal: Goal, foco: List<MuscleGroup>): List<MuscleGroup> =
        // `temAlvoDeVolume` ANTES do `take`: um CORE legado no perfil não pode gastar uma das 2 vagas de foco.
        // O oráculo não tem CORE no vocabulário (a matriz nunca o gera), então isto não muda nenhuma referência.
        if (level == Level.BEGINNER || goal == Goal.GENERAL_HEALTH) emptyList()
        else foco.filter { it.temAlvoDeVolume }.take(FOCO_MAX_GRUPOS)

    /**
     * Reparte [total] séries entre [alvos] proporcionalmente ao `PESO_PRIORIDADE`, em inteiros, por
     * maior resto (soma exata == total). Aritmética inteira (peso ×100) para Kotlin e Python não
     * divergirem em empate; desempate pela ordem de [TargetMuscle]. Espelha `distribuir_bonus()`.
     */
    fun distribuirBonus(total: Int, alvos: List<TargetMuscle>): Map<TargetMuscle, Int> {
        val pesos = alvos.associateWith { Math.round(PriorityAllocation.PESO_PRIORIDADE.getValue(it) * 100).toInt() }
        val soma = pesos.values.sum()
        val parte = alvos.associateWith { total * pesos.getValue(it) / soma }.toMutableMap()
        val resto = total - parte.values.sum()
        alvos.sortedWith(compareBy<TargetMuscle>({ -(total * pesos.getValue(it) % soma) }, { it.ordinal }))
            .take(resto).forEach { parte[it] = parte.getValue(it) + 1 }
        return parte
    }

    /**
     * UM bônus por ESCOLHA do usuário: +6 se o grupo cobre algum alvo grande, senão +3. Grupo de um
     * alvo só recebe o valor cheio; grupo que cobre vários alvos (`LEGS`) reparte o valor ÚNICO entre
     * eles pelo peso — nada de somar o bônus cheio em cada alvo (`LEGS` = +3/+2/+1, total +6).
     *
     * **Só o bônus de alvo.** A fase de dois mesociclos, o piso nos demais grupos e a proteção na
     * alocação são da Fatia 11: até lá o foco é PERMANENTE e, medido, quase inerte (a capacidade é
     * o limite — ver `docs/historico/motor-v3-fatia-10.md`). Espelha `motor_v3.py::bonus_de_foco()`.
     */
    fun bonusDeFoco(level: Level, goal: Goal, foco: List<MuscleGroup>): Map<TargetMuscle, Int> {
        val out = mutableMapOf<TargetMuscle, Int>()
        for (g in focoEfetivo(level, goal, foco)) {
            val alvos = alvosDoFoco(g).sortedBy { it.ordinal }
            val total = if (alvos.any { it in GRANDES }) FOCO_BONUS_GRANDE else FOCO_BONUS_PEQUENO
            distribuirBonus(total, alvos).forEach { (m, v) -> out[m] = maxOf(out[m] ?: 0, v) }
        }
        return out
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

    /**
     * Piso DIRETO (`[REGRA]`, Fatia 8 / D-1, decisão do Rafael 2026-10-27): séries **diretas**
     * por semana garantidas nos músculos que recebem crédito indireto sancionado.
     *
     * Não é para completar volume — esses casos já saem [ProgramAudit.Status.ATENDIDO] pelo
     * efetivo. É para garantir ESTÍMULO DIRETO onde o composto não alcança: Brandão et al. 2020
     * mediu a cabeça longa do tríceps crescendo +2,1% só com supino contra +17,5% com extensão.
     *
     * **2, não 4**, medido antes de decidir (regra de decisão fixada pelo Rafael antes da
     * medição): o piso de 4 era honrado em 228/480 casos elegíveis contra 324/480 do piso de 2 —
     * piso honrado em menos da metade dos casos não é garantia, é aspiração.
     *
     * Iniciante fica **isento** (regra geral do híbrido, Iversen 2021/Gentil): ele cresce com o
     * composto, e o orçamento dele é o mais apertado. Preço medido da isenção: 57 pares
     * BEGINNER/TRICEPS saem ATENDIDO com zero série direta.
     *
     * Financiado SEMPRE dentro do orçamento, nunca por fora
     * (ver [PriorityAllocation.pisoPrescritivel]).
     */
    const val PISO_DIRETO = 2

    /** Os três que recebem crédito indireto sancionado — ver [VolumeIndirectTable]. */
    val PISO_DIRETO_MUSCULOS: Set<TargetMuscle> = setOf(
        TargetMuscle.TRICEPS, TargetMuscle.BICEPS, TargetMuscle.GLUTES,
    )

    val PISO_DIRETO_NIVEIS: Set<Level> = setOf(Level.INTERMEDIATE, Level.ADVANCED)

    /** Espelha `motor_v3.py::piso_direto()`. */
    fun pisoDireto(muscle: TargetMuscle, level: Level): Int =
        if (level in PISO_DIRETO_NIVEIS && muscle in PISO_DIRETO_MUSCULOS) PISO_DIRETO else 0
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
     * Os músculos que RECEBEM crédito indireto de algum doador.
     *
     * Exposto de propósito — e só isto, não a tabela — para o `[INV]` que amarra esta tabela ao
     * piso de trabalho direto ([VolumeTargets.PISO_DIRETO_MUSCULOS]): o piso existe PARA compensar
     * o crédito indireto, então um recebedor novo aqui obriga uma decisão lá (entrar na lista, ou
     * a exclusão ser decidida e registrada). Ver `VolumeTargetsTest`.
     */
    val recebedoresDeCredito: Set<TargetMuscle> = coefficients.values.flatMap { it.keys }.toSet()

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
