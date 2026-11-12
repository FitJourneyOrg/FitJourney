package dev.rafael.server.features.exercise.engine.v3

import dev.rafael.contract.profile.Goal
import dev.rafael.contract.workout.Papel

/**
 * Fatia "sugestao-de-progressao" (2026-11-06, `docs/trabalho/sugestao-de-progressao-plano.md`),
 * estendida na fatia "mostrar sugestão de nível" (2026-11-07) pro 3º eixo. Núcleo puro: lê o
 * [Estado][Progressao.Estado] (ou o equivalente de repetição) que o motor JÁ decidiu e só
 * projeta — não decide nada, não grava nada, não sabe de banco. Espelha o piso de eixo do
 * `ProgressaoAplicadaService`: `incremento != null` → eixo carga; `incremento == null &&
 * equipment == "BODYWEIGHT"` → eixo repetição; `incremento == null && equipment in (BAND,
 * HAND_GRIP)` → eixo nível; qualquer outra combinação → sem sugestão.
 *
 * `papel == null` (treino manual, sem prescrição) → [Sugestao.VAZIA]: sem papel não há
 * [PrescricaoV3.Faixa] original pra cair na 1ª exposição, mesmo piso do `ProgressaoAplicadaService`
 * ("sem papel pula o exercício").
 *
 * Eixo carga sem [Progressao.Estado] (nunca treinou — calibração pendente) também devolve
 * [Sugestao.VAZIA]: **decisão do Rafael (2026-11-06)** — mostrar uma carga antes da calibração
 * seria inventar um número que o motor não calculou.
 *
 * Eixo repetição sem [ProgressaoPorRepeticao.Estado] (1ª exposição) NÃO é vazio: cai na
 * [PrescricaoV3.Faixa] original (`PrescricaoV3.de(goal, papel)`) — é a mesma faixa que o motor vai
 * usar pra calibrar na primeira sessão, então já dá pra mostrar.
 *
 * **Eixo nível não precisa de [ProgressaoPorNivel.Estado] nenhum** — ao contrário do eixo de
 * repetição, a faixa de reps deste eixo é FIXA pra vida do exercício (`nivelAtual` é quem sobe/
 * desce, nunca a faixa; ver `ProgressaoPorNivel`, V77). Por isso a sugestão aqui é sempre a
 * mesma [PrescricaoV3.Faixa] original, estado ou não — e `nivelAtual` nunca aparece pro usuário
 * como número (`fontes/debitos.md`).
 */
object SugestaoDeProgressao {

    data class Sugestao(
        val cargaKg: Double? = null,
        val repsMin: Int? = null,
        val repsMax: Int? = null,
    ) {
        companion object {
            val VAZIA = Sugestao()
        }
    }

    fun calcular(
        incremento: Double?,
        equipment: String?,
        papel: Papel?,
        goal: Goal,
        estadoCarga: Progressao.Estado?,
        estadoRepeticao: ProgressaoPorRepeticao.Estado?,
    ): Sugestao {
        if (papel == null) return Sugestao.VAZIA
        return when {
            incremento != null ->
                estadoCarga?.carga?.let { Sugestao(cargaKg = it) } ?: Sugestao.VAZIA
            equipment == "BODYWEIGHT" -> {
                val faixa = estadoRepeticao
                    ?: PrescricaoV3.de(goal, papel).let {
                        ProgressaoPorRepeticao.Estado(repsMin = it.repsMin, repsMax = it.repsMax)
                    }
                Sugestao(repsMin = faixa.repsMin, repsMax = faixa.repsMax)
            }
            equipment == "BAND" || equipment == "HAND_GRIP" -> {
                val faixa = PrescricaoV3.de(goal, papel)
                Sugestao(repsMin = faixa.repsMin, repsMax = faixa.repsMax)
            }
            else -> Sugestao.VAZIA
        }
    }
}
