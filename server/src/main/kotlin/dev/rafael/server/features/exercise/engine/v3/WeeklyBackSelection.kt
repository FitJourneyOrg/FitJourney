package dev.rafael.server.features.exercise.engine.v3

import dev.rafael.server.features.exercise.engine.TargetMuscle
import dev.rafael.server.features.exercise.models.Exercise
import dev.rafael.server.features.exercise.models.MovementPattern

/**
 * Motor v3, Fatia 11 — seleção semanal de BACK. Espelha
 * `server/src/test/oracle/motor_v3.py::aplicar_puxada_horizontal_semanal()` termo a termo.
 */
object WeeklyBackSelection {

    /**
     * `[REGRA]` (Fatia 11, custo zero em capacidade — troca exercício na vaga existente, não soma
     * série): com 2 ou mais vagas de BACK na semana, pelo menos uma é `HORIZONTAL_PULL`. Com 1
     * vaga a regra NÃO se aplica — não há o que balancear (disparo
     * `puxada_horizontal_regra_nao_aplicavel`). Mesma forma da restrição de extensão de cotovelo
     * do tríceps (Fatia 8): o motor decide QUANTO, esta regra decide QUAL.
     *
     * Medido no oráculo (720/960 perfis): o pool nunca fica vazio em ACADEMIA; em CASA sem
     * acessório, 50% dos perfis nunca têm `VERTICAL_PULL` (falta de equipamento — Barra Fixa e
     * Puxada Alta são as duas mais prioritárias de BACK e ambas verticais), por isso não existe
     * regra espelho exigindo `VERTICAL_PULL` (exigiria candidato impossível em CASA).
     */
    data class Resultado(
        val sessoes: List<Pair<String, List<CatalogFiller.VagaPreenchida>>>,
        val disparos: Map<String, Int>,
    )

    fun aplicar(
        sessoes: List<Pair<String, List<CatalogFiller.VagaPreenchida>>>,
        candidatos: List<Exercise>,
        disparos: Map<String, Int>,
    ): Resultado {
        val d = disparos.toMutableMap()
        val vagasBack = sessoes.withIndex().flatMap { (diaIdx, par) ->
            par.second.withIndex().mapNotNull { (vagaIdx, vp) ->
                if (vp.vaga.alvo == TargetMuscle.BACK && vp.exercicio != null) Triple(diaIdx, vagaIdx, vp) else null
            }
        }
        if (vagasBack.size < 2) {
            d["puxada_horizontal_regra_nao_aplicavel"] = (d["puxada_horizontal_regra_nao_aplicavel"] ?: 0) + 1
            return Resultado(sessoes, d)
        }
        if (vagasBack.none { it.third.exercicio!!.movementPattern == MovementPattern.VERTICAL_PULL }) {
            d["puxada_vertical_semanal_ausente"] = (d["puxada_vertical_semanal_ausente"] ?: 0) + 1
        }
        if (vagasBack.any { it.third.exercicio!!.movementPattern == MovementPattern.HORIZONTAL_PULL }) {
            return Resultado(sessoes, d)
        }
        d["puxada_horizontal_semanal_corrigida"] = (d["puxada_horizontal_semanal_corrigida"] ?: 0) + 1

        val usadosPorDia = sessoes.map { (_, vagas) -> vagas.mapNotNull { it.exercicio?.name }.toSet() }
        val usadosSemana = sessoes.flatMap { it.second }.mapNotNull { it.exercicio?.name }
            .groupingBy { it }.eachCount()
        val familiasPorDia = sessoes.map { (_, vagas) -> vagas.mapNotNull { it.exercicio?.familia }.toSet() }

        data class Candidata(val diaIdx: Int, val vagaIdx: Int, val escolha: Exercise)

        val escolhasPorVaga = vagasBack.mapNotNull { (diaIdx, vagaIdx, vp) ->
            val querComposto = vp.vaga.papel == SessionComposition.Papel.COMPOSTO ||
                vp.vaga.papel == SessionComposition.Papel.ACESSORIO
            var cands = candidatos.filter {
                it.volumeTarget != null && TargetMuscle.valueOf(it.volumeTarget!!) == TargetMuscle.BACK &&
                    it.isCompound == querComposto &&
                    it.movementPattern == MovementPattern.HORIZONTAL_PULL &&
                    it.name !in usadosPorDia[diaIdx]
            }
            if (cands.isEmpty()) return@mapNotNull null
            val ineditos = cands.filter { (it.familia ?: "") !in familiasPorDia[diaIdx] }
            if (ineditos.isNotEmpty()) cands = ineditos
            val base = cands.filter { it.relevancia == 1 }
            if (base.isNotEmpty()) cands = base
            val escolha = cands.minWithOrNull(
                compareBy({ usadosSemana[it.name] ?: 0 }, { it.prioridade ?: Int.MAX_VALUE }),
            ) ?: return@mapNotNull null
            Candidata(diaIdx, vagaIdx, escolha)
        }

        val melhor = escolhasPorVaga.minWithOrNull(
            compareBy({ usadosSemana[it.escolha.name] ?: 0 }, { it.escolha.prioridade ?: Int.MAX_VALUE }),
        )
        if (melhor == null) {
            d["puxada_horizontal_sem_candidato"] = (d["puxada_horizontal_sem_candidato"] ?: 0) + 1
            return Resultado(sessoes, d)
        }

        val sessoesNovas = sessoes.mapIndexed { i, (rotulo, vagas) ->
            if (i != melhor.diaIdx) {
                rotulo to vagas
            } else {
                rotulo to vagas.mapIndexed { j, vp -> if (j == melhor.vagaIdx) vp.copy(exercicio = melhor.escolha) else vp }
            }
        }
        return Resultado(sessoesNovas, d)
    }
}
