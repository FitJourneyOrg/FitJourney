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
        TargetMuscle.GLUTES to V(4, 8, 12, 16),
        // Abdômen foi a zero (seu item 28): reservar capacidade que o catálogo não enche.
        TargetMuscle.CORE to V(0, 0, 0, null),
    )

    /** Alvo semanal por músculo/nível. GENERAL_HEALTH usa o piso (MEV), igual ao motor antigo. */
    fun weeklySets(muscle: TargetMuscle, level: Level, goal: Goal): Int {
        val v = table.getValue(muscle)
        if (goal == Goal.GENERAL_HEALTH) return v.ini
        return when (level) {
            Level.BEGINNER -> v.ini
            Level.INTERMEDIATE -> v.inter
            Level.ADVANCED -> v.adv
        }
    }

    /** Teto (MRV). `null` = sem teto definido ainda (abdômen, até o catálogo ter exercícios). */
    fun mrv(muscle: TargetMuscle): Int? = table.getValue(muscle).mrv

    /**
     * Piso (coluna do iniciante), independente do nível/objetivo do usuário — é o piso que
     * a alocação sob escassez usa (Fatia 3): `min(direta, max(0, piso − indireto))`.
     */
    fun piso(muscle: TargetMuscle): Int = table.getValue(muscle).ini
}

/**
 * Volume indireto: o motor conta meia série para o sinergista a cada série de composto do
 * doador. Duas exceções documentadas na especificação — ombro não entra como doador (o alvo
 * "ombro" é o deltoide inteiro, descontar subserviria lateral/posterior) e glúteo→posterior é
 * 0,2, não 0,5 (elevação pélvica/abdução treinam isquiotibial pouco).
 */
object VolumeIndirectTable {
    /** doador -> (recipiente -> coeficiente). */
    private val coefficients: Map<TargetMuscle, Map<TargetMuscle, Double>> = mapOf(
        TargetMuscle.CHEST to mapOf(TargetMuscle.TRICEPS to 0.5),
        TargetMuscle.SHOULDERS to mapOf(TargetMuscle.TRICEPS to 0.5),
        TargetMuscle.BACK to mapOf(TargetMuscle.BICEPS to 0.5, TargetMuscle.FOREARMS to 0.5),
        TargetMuscle.POSTERIOR to mapOf(TargetMuscle.GLUTES to 0.5, TargetMuscle.FOREARMS to 0.5),
        TargetMuscle.QUADS to mapOf(TargetMuscle.GLUTES to 0.5),
        TargetMuscle.GLUTES to mapOf(TargetMuscle.POSTERIOR to 0.2),
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
