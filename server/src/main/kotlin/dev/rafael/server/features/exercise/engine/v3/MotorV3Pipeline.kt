package dev.rafael.server.features.exercise.engine.v3

import dev.rafael.contract.profile.Accessory
import dev.rafael.contract.profile.Goal
import dev.rafael.contract.profile.Level
import dev.rafael.contract.profile.TrainingEnvironment
import dev.rafael.server.features.exercise.engine.TargetMuscle
import dev.rafael.server.features.exercise.models.Exercise

/**
 * Motor v3 — orquestra as Fatias 1 a 6 em volta do [PontoFixoIndireto]. Espelha
 * `tools/motor_sim/motor_v3.py::gerar()` termo a termo. **Não está ligado em produção** — isso
 * é decisão pra depois da Fatia 9 (ver `regras/01-motor-de-geracao.md`).
 */
object MotorV3Pipeline {

    data class Entrada(
        val objetivo: Goal,
        val nivel: Level,
        val dias: Int,
        val minutos: Int,
        val idade: Int?,
        val mesocicloAtual: Int?,
        val ambiente: TrainingEnvironment,
        val acessorios: Set<Accessory>,
    )

    data class Estado(
        val alocado: Map<TargetMuscle, Int>,
        val sessoes: List<Pair<String, List<CatalogFiller.VagaPreenchida>>>,
        val entregue: Map<TargetMuscle, Int>,
        val compostosSemana: Map<TargetMuscle, Int>,
        val vagasVazias: List<Triple<String, TargetMuscle, SessionComposition.Papel>>,
    )

    data class Resultado(
        val divisao: String,
        val capacidade: Double,
        val estado: Estado,
        val indireto: Map<TargetMuscle, Double>,
        val disparos: Map<String, Int>,
        val iteracoes: Int,
        /** Etapa [7]: o que cada músculo de fato recebeu, e se basta. Ver [ProgramAudit]. */
        val auditoria: Map<TargetMuscle, ProgramAudit.Musculo>,
    )

    fun gerar(entrada: Entrada, catalogoCompleto: List<Exercise>): Resultado {
        val (divisao, dias) = WeekStructure.divisao(entrada.dias)
        val alvo = TargetMuscle.entries.associateWith {
            VolumeTargets.weeklySets(it, entrada.nivel, entrada.objetivo)
        }
        val teto = CapacityModel.tetoDaSessao(
            minutos = entrada.minutos, dias = entrada.dias, nivel = entrada.nivel,
            goal = entrada.objetivo, idade = entrada.idade, mesocicloAtual = entrada.mesocicloAtual,
        )
        val capacidade = teto * dias.size

        val candidatos = CatalogPool.pool(catalogoCompleto, entrada.ambiente, entrada.nivel, entrada.acessorios)
        val naDivisao = dias.flatMap { it.musculos }.toSet()
        val alvosNoCatalogo = candidatos.mapNotNull { it.volumeTarget }.map { TargetMuscle.valueOf(it) }.toSet()
        val treinaveis = TargetMuscle.entries.filter { it in naDivisao && it in alvosNoCatalogo }

        // Fatia 7: calculado uma vez por perfil, não por sessão — espelha
        // `motor_v3.py::preencher()`, que chama `modo_conservador_automatico(p)` uma vez no topo.
        val modoConservador = SafetyRules.modoConservadorAutomatico(
            nivel = entrada.nivel, objetivo = entrada.objetivo, idade = entrada.idade,
            mesocicloAtual = entrada.mesocicloAtual,
        )

        val pisoDiretoDe = TargetMuscle.entries.associateWith { VolumeTargets.pisoDireto(it, entrada.nivel) }

        val resultado = PontoFixoIndireto.iterar<Estado> { indireto ->
            val disparos = mutableMapOf<String, Int>()
            if (modoConservador) disparos["modo_conservador_ativo"] = 1
            val vazias = mutableListOf<Triple<String, TargetMuscle, SessionComposition.Papel>>()

            // Piso direto (Fatia 8 / D-1): entra na demanda ANTES da alocação, para que o
            // orçamento seja disputado com ele dentro — nunca somado depois.
            val direta = TargetMuscle.entries.associateWith { m ->
                maxOf(pisoDiretoDe.getValue(m).toDouble(), demandaDireta(alvo.getValue(m), indireto[m] ?: 0.0))
            }
            val alocado = PriorityAllocation.alocar(direta, indireto, treinaveis, capacidade, disparos, pisoDiretoDe)
            val semana = WeekStructure.distribuirPelosDias(alocado, dias, disparos)
            val sessoesVagas = dias.zip(semana).map { (dia, series) -> dia.rotulo to SessionComposition.comporSessao(series, disparos) }
            val preenchidas = CatalogFiller.preencher(sessoesVagas, candidatos, disparos, vazias, modoConservador)
            val (entregue, compostos, novo) = medirEntregue(preenchidas)

            Triple(Estado(alocado, preenchidas, entregue, compostos, vazias), novo, disparos)
        }

        return Resultado(
            divisao, capacidade, resultado.estado, resultado.indireto, resultado.disparos,
            resultado.iteracoes,
            auditoria = ProgramAudit.auditar(
                alvo = alvo, entregue = resultado.estado.entregue,
                indireto = resultado.indireto, treinaveis = treinaveis,
                objetivo = entrada.objetivo, pisoDiretoDe = pisoDiretoDe,
            ),
        )
    }

    /** Espelha `motor_v3.py::medir_entregue()` — mede sobre o exercício REAL escolhido, nunca
     *  sobre o papel, e devolve o indireto como mapa COMPLETO (todo [TargetMuscle], default
     *  0.0) -- é o que permite comparar `novo == indireto` no ponto fixo igual a um dict Python
     *  inicializado com `{m: 0.0 for m in ALVOS}`. */
    private fun medirEntregue(
        sessoes: List<Pair<String, List<CatalogFiller.VagaPreenchida>>>,
    ): Triple<Map<TargetMuscle, Int>, Map<TargetMuscle, Int>, Map<TargetMuscle, Double>> {
        val entregue = mutableMapOf<TargetMuscle, Int>()
        val compostos = mutableMapOf<TargetMuscle, Int>()
        for ((_, vagas) in sessoes) {
            for (vp in vagas) {
                val exercicio = vp.exercicio ?: continue
                entregue[vp.vaga.alvo] = (entregue[vp.vaga.alvo] ?: 0) + vp.vaga.series
                if (exercicio.isCompound == true) {
                    compostos[vp.vaga.alvo] = (compostos[vp.vaga.alvo] ?: 0) + vp.vaga.series
                }
            }
        }
        val indiretoParcial = VolumeIndirectTable.indiretoRecebido(compostos.mapValues { it.value.toDouble() })
        val indiretoCompleto = TargetMuscle.entries.associateWith { indiretoParcial[it] ?: 0.0 }
        return Triple(entregue, compostos, indiretoCompleto)
    }
}
