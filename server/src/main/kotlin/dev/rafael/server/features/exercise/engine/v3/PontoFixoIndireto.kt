package dev.rafael.server.features.exercise.engine.v3

import dev.rafael.server.features.exercise.engine.TargetMuscle

/**
 * Motor v3 — o ciclo do ponto fixo do indireto, abertura da Fatia 6 (movido da Fatia 5,
 * `docs/trabalho/motor-v3-desenho-e-fatias.md`). Mecânica pura, sem saber de alocação,
 * estrutura, composição ou catálogo — quem conhece o pipeline é [MotorV3Pipeline], que passa um
 * `passo` aqui. Separar os dois é o que torna o caminho "esgota as 30 iterações" testável: o
 * oráculo nunca bate isso nos 720 perfis reais (máximo observado: 5), então só um `passo` falso
 * alcança esse caminho — mas a mecânica do laço (converge / oscila / esgota) é a mesma.
 *
 * Espelha `tools/motor_sim/motor_v3.py::gerar()` (o laço `for it in range(1, 31): ... else:
 * disparos["ponto_fixo_nao_convergiu"] += 1`), termo a termo.
 */
object PontoFixoIndireto {

    const val LIMITE_ITERACOES = 30

    data class Resultado<T>(
        val estado: T,
        val indireto: Map<TargetMuscle, Double>,
        val disparos: Map<String, Int>,
        val iteracoes: Int,
    )

    /**
     * @param indiretoInicial o indireto da primeira iteração — `0.0` para todo músculo no uso
     *   real; um teste de mecânica pode injetar outro ponto de partida.
     * @param passo dado o indireto de ENTRADA desta iteração, devolve o estado (o que o chamador
     *   quer guardar), o indireto medido sobre o entregue (`novo`) e os disparos desta iteração
     *   — fresh a cada chamada, nunca acumulado entre iterações (mesma convenção do oráculo:
     *   `disparos = Counter(disparos_pool)` no topo do laço).
     */
    fun <T> iterar(
        indiretoInicial: Map<TargetMuscle, Double> = TargetMuscle.entries.associateWith { 0.0 },
        passo: (Map<TargetMuscle, Double>) -> Triple<T, Map<TargetMuscle, Double>, MutableMap<String, Int>>,
    ): Resultado<T> {
        var indireto = indiretoInicial
        // guarda o indireto de ENTRADA de cada iteração já processada (não o resultado) --
        // é contra essa lista que o `novo` de uma iteração nova é comparado, igual ao oráculo.
        val historico = mutableListOf<Pair<Map<TargetMuscle, Double>, Triple<T, Map<TargetMuscle, Double>, MutableMap<String, Int>>>>()

        for (it in 1..LIMITE_ITERACOES) {
            val estadoAtual = passo(indireto)
            val novo = estadoAtual.second

            if (novo == indireto) {
                return Resultado(estadoAtual.first, novo, estadoAtual.third, it)
            }

            val indiceCiclo = historico.indexOfFirst { it.first == novo }
            if (indiceCiclo >= 0) {
                // Ciclo: o indireto que acabamos de medir já foi usado como ENTRADA antes --
                // continuar só repetiria os mesmos estados. Fica o de menor indireto total (o
                // que menos supõe trabalho não feito), desempatando por uma chave estável.
                val ciclo = historico.subList(indiceCiclo, historico.size).map { it.second } + estadoAtual
                val escolhido = ciclo.minWithOrNull(
                    compareBy({ e -> e.second.values.sum() }, { e -> chaveDeDesempate(e.second) }),
                )!!
                escolhido.third["ponto_fixo_oscilou"] = (escolhido.third["ponto_fixo_oscilou"] ?: 0) + 1
                return Resultado(escolhido.first, escolhido.second, escolhido.third, it)
            }

            historico.add(indireto to estadoAtual)
            indireto = novo
        }

        val ultimo = historico.last().second
        ultimo.third["ponto_fixo_nao_convergiu"] = (ultimo.third["ponto_fixo_nao_convergiu"] ?: 0) + 1
        return Resultado(ultimo.first, ultimo.second, ultimo.third, LIMITE_ITERACOES)
    }

    private fun chaveDeDesempate(indireto: Map<TargetMuscle, Double>): String =
        indireto.entries.sortedBy { it.key.name }.joinToString(";") { "${it.key.name}:${it.value}" }
}
