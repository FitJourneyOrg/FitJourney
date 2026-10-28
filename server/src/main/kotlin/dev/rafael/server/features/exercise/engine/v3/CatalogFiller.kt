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
     * Famílias de tríceps que NÃO são extensão de cotovelo — ver a `[REGRA]` em [preencher].
     * `MERGULHO` é empurrar composto (o cotovelo estende, mas o ombro também, e a carga sai do
     * peitoral/deltoide); as outras quatro famílias do catálogo (FRANCES, TESTA, PULLEY, COICE)
     * são todas extensão.
     */
    private val TRICEPS_SEM_EXTENSAO = setOf("MERGULHO")

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

                // `[REGRA]` tríceps (Fatia 8, 2026-10-27): a vaga de ISOLAMENTO tem de ser
                // extensão de cotovelo — fora o MERGULHO, que é empurrar composto e só chega aqui
                // pela inversão de papel. Brandão 2020: cabeça longa +2,1% só com supino contra
                // +17,5% com extensão. Exigência, não preferência: se excluir o MERGULHO esvaziar
                // o pool, ele volta e o disparo registra — melhor mergulho que vaga vazia.
                //
                // A preferência por ACIMA DA CABEÇA **não mora aqui** (decisão do Rafael, opção
                // 4): implementada como filtro, ela rodava ANTES da regra da relevância 1 e
                // esvaziava o pool — 471/471 vagas em 2 exercícios, com TRICEPS_PULLEY
                // (relevância 1, prioridade 1) inalcançável nos 720 perfis. Motor decide QUANTO;
                // curadoria decide QUAL. Virou `[PROPOSTA]` em `docs/fontes/debitos.md`.
                if (v.alvo == TargetMuscle.TRICEPS && v.papel == SessionComposition.Papel.ISOLAMENTO) {
                    val comExtensao = cands.filter { (it.familia ?: "") !in TRICEPS_SEM_EXTENSAO }
                    cands = if (comExtensao.isNotEmpty()) {
                        comExtensao
                    } else {
                        if (cands.isNotEmpty()) {
                            disparos["triceps_sem_extensao_de_cotovelo"] = (disparos["triceps_sem_extensao_de_cotovelo"] ?: 0) + 1
                        }
                        cands
                    }
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
