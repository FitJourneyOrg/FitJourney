package dev.rafael.server.features.exercise.engine.v3

import dev.rafael.server.features.exercise.engine.TargetMuscle
import dev.rafael.server.features.exercise.models.Exercise

/**
 * Motor v3, etapa [6] — escolhe o exercício REAL do catálogo para cada [SessionComposition.Vaga],
 * espelhando `tools/motor_sim/motor_v3.py::preencher()`, com o fallback de inversão de papel
 * decidido pelo Rafael em 2026-10-23 (`docs/regras/01-motor-de-geracao.md`, etapa [6]).
 */
object CatalogFiller {

    data class VagaPreenchida(
        val vaga: SessionComposition.Vaga,
        val exercicio: Exercise?,
        // --- Fatia 7: piso de RIR de segurança aplicado a esta vaga. null = vaga vazia ou RIR
        // normal do objetivo (fora do escopo desta fatia -- ver SafetyRules.rirMinimo).
        val rir: Int? = null,
    )

    private val EMPURRAR = setOf(TargetMuscle.CHEST, TargetMuscle.SHOULDERS)

    /**
     * @param sessoes rótulo do dia + vagas da sessão (saída de [SessionComposition.comporSessao]).
     * @param candidatos pool já filtrado (ver [CatalogPool]).
     * @param disparos contadores de regra provisória/medida — ver KDoc de cada incremento.
     * @param vaziasOut vagas que não acharam candidato nenhum, nem depois da inversão.
     */
    fun preencher(
        sessoes: List<Pair<String, List<SessionComposition.Vaga>>>,
        candidatos: List<Exercise>,
        disparos: MutableMap<String, Int> = mutableMapOf(),
        vaziasOut: MutableList<Triple<String, TargetMuscle, SessionComposition.Papel>> = mutableListOf(),
        // --- Fatia 7: calculado uma vez pelo chamador (MotorV3Pipeline), mesmo gatilho do
        // oráculo (`modo_conservador_automatico`). false por padrão só para não quebrar quem
        // ainda chama preencher() sem passar isto (testes existentes).
        modoConservador: Boolean = false,
    ): List<Pair<String, List<VagaPreenchida>>> {
        val usadosSemana = mutableMapOf<String, Int>()
        val resultado = mutableListOf<Pair<String, List<VagaPreenchida>>>()

        for ((rotulo, vagas) in sessoes) {
            val usadosDia = mutableSetOf<String>()
            val familiasDoDia = mutableSetOf<String>()
            val musculosAbertos = mutableSetOf<TargetMuscle>()
            var empurrarComBarra = false
            val preenchidas = mutableListOf<VagaPreenchida>()

            for (v in vagas) {
                fun serve(e: Exercise, composto: Boolean) =
                    TargetMuscle.valueOf(e.volumeTarget!!) == v.alvo &&
                        e.isCompound == composto &&
                        e.name !in usadosDia

                val querComposto = v.papel == SessionComposition.Papel.COMPOSTO ||
                    v.papel == SessionComposition.Papel.ACESSORIO
                var cands = candidatos.filter { serve(it, querComposto) }
                if (cands.isEmpty()) {
                    // [REGRA] fallback: inverte o tipo nos dois sentidos, disparo papel_trocado.
                    val invertidos = candidatos.filter { serve(it, !querComposto) }
                    if (invertidos.isNotEmpty()) {
                        disparos["papel_trocado"] = (disparos["papel_trocado"] ?: 0) + 1
                    }
                    cands = invertidos
                }

                if (v.alvo in EMPURRAR && empurrarComBarra) {
                    val semBarra = cands.filter { !(it.isCompound == true && it.equipment == "BARBELL") }
                    if (semBarra.size < cands.size) {
                        disparos["um_empurrar_com_barra_por_sessao"] =
                            (disparos["um_empurrar_com_barra_por_sessao"] ?: 0) + 1
                    }
                    cands = semBarra
                }

                val ineditos = cands.filter { (it.familia ?: "") !in familiasDoDia }
                cands = if (ineditos.isNotEmpty()) {
                    ineditos
                } else {
                    if (cands.isNotEmpty()) {
                        disparos["familia_repetida_na_sessao"] = (disparos["familia_repetida_na_sessao"] ?: 0) + 1
                    }
                    cands
                }

                if (v.alvo !in musculosAbertos) {
                    val base = cands.filter { it.relevancia == 1 }
                    cands = if (base.isNotEmpty()) {
                        base
                    } else {
                        if (cands.isNotEmpty()) {
                            disparos["musculo_sem_relevancia_1"] = (disparos["musculo_sem_relevancia_1"] ?: 0) + 1
                        }
                        cands
                    }
                }

                if (cands.isEmpty()) {
                    vaziasOut.add(Triple(rotulo, v.alvo, v.papel))
                    preenchidas.add(VagaPreenchida(v, null))
                    continue
                }

                // Desempate: usados na semana -> prioridade -> nome. Os dois primeiros vêm do
                // oráculo; o nome é um terceiro critério explícito que o Python não documenta
                // (decisão do Rafael) -- garante saída idêntica mesmo com o catálogo embaralhado.
                val escolhido = cands.minWithOrNull(
                    compareBy(
                        { usadosSemana[it.name] ?: 0 },
                        { it.prioridade ?: Int.MAX_VALUE },
                        { it.name },
                    ),
                )!!
                val rir = SafetyRules.rirMinimo(escolhido, modoConservador)
                when (rir) {
                    3 -> disparos["rir_3_modo_conservador"] = (disparos["rir_3_modo_conservador"] ?: 0) + 1
                    2 -> disparos["rir_2_por_propriedade"] = (disparos["rir_2_por_propriedade"] ?: 0) + 1
                    else -> {}
                }
                preenchidas.add(VagaPreenchida(v, escolhido, rir))
                usadosDia += escolhido.name
                familiasDoDia += (escolhido.familia ?: "")
                musculosAbertos += v.alvo
                usadosSemana[escolhido.name] = (usadosSemana[escolhido.name] ?: 0) + 1
                if (v.alvo in EMPURRAR && escolhido.isCompound == true && escolhido.equipment == "BARBELL") {
                    empurrarComBarra = true
                }
            }

            // [REGRA] ordem final pelo tipo ENTREGUE (composto antes de isolado), não pelo papel
            // original -- um composto que entrou por inversão abre a sessão como qualquer outro.
            // sortedByDescending é stable: dentro do mesmo grupo, mantém a ordem de composição.
            val ordenadas = preenchidas.sortedByDescending { it.exercicio?.isCompound == true }
            resultado.add(rotulo to ordenadas)
        }
        return resultado
    }
}
