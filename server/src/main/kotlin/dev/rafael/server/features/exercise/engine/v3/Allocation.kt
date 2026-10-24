package dev.rafael.server.features.exercise.engine.v3

import dev.rafael.server.features.exercise.engine.TargetMuscle
import kotlin.math.floor

/**
 * Motor v3, etapa [3] — ALOCAÇÃO POR PRIORIDADE (`docs/trabalho/motor-v3-desenho-e-fatias.md`
 * §3, Fatia 3). Espelha `tools/motor_sim/motor_v3.py::alocar()` termo a termo — mesmo
 * algoritmo, mesma ordem de sacrifício e os mesmos pesos (`spec_v3.py`), validado contra ela
 * (`docs/trabalho/motor-v3-fatia-3.md`).
 *
 * Recebe `direta`/`indireto`/`capacidade` já resolvidos, mesmo padrão das Fatias 1 e 2.
 * **O ciclo do ponto fixo do indireto NÃO é desta fatia** — ele precisa de séries de composto
 * de verdade, que só existem com a composição (Fatia 5); é lá que o laço entra. `alocar()` aqui
 * é uma única passada, pura e determinística, para um `direta`/`indireto` fixos.
 */
object PriorityAllocation {

    /** "Como alocamos" — ordem de sacrifício declarada na especificação. */
    val ORDEM_SACRIFICIO: List<TargetMuscle> = listOf(
        TargetMuscle.FOREARMS, TargetMuscle.CALVES, TargetMuscle.CORE, TargetMuscle.GLUTES,
        TargetMuscle.BICEPS, TargetMuscle.TRICEPS, TargetMuscle.SHOULDERS,
    )

    /**
     * Peso do excedente proporcional (especificação + `spec_v3.py`). Músculos fora de
     * `ORDEM_SACRIFICIO` (CHEST, BACK, QUADS, POSTERIOR) nunca são rebaixados nem sacrificados —
     * ficam protegidos no piso mesmo quando ele, por si só, excede a capacidade (ver
     * `alocacao sob escassez extrema nao garante caber na capacidade` no teste).
     */
    val PESO_PRIORIDADE: Map<TargetMuscle, Double> = mapOf(
        TargetMuscle.CHEST to 1.0, TargetMuscle.BACK to 1.1, TargetMuscle.SHOULDERS to 0.9,
        TargetMuscle.QUADS to 1.0, TargetMuscle.POSTERIOR to 0.9, TargetMuscle.GLUTES to 0.7,
        TargetMuscle.BICEPS to 0.5, TargetMuscle.TRICEPS to 0.55, TargetMuscle.FOREARMS to 0.2,
        TargetMuscle.CALVES to 0.4, TargetMuscle.CORE to 0.5,
    )

    private val SO_ISOLAMENTO = setOf(
        TargetMuscle.BICEPS, TargetMuscle.TRICEPS, TargetMuscle.FOREARMS,
        TargetMuscle.CALVES, TargetMuscle.CORE,
    )

    /** Piso de séries do primeiro papel: 3 no composto, 2 num músculo só-isolamento. */
    fun pisoDoPapel(musculo: TargetMuscle): Int = if (musculo in SO_ISOLAMENTO) 2 else 3

    private const val EPS = 1e-9

    /**
     * Aloca `direta` sob `capacidade`, dado `indireto` e `treinaveis` (músculos que a divisão da
     * semana efetivamente treina nesta configuração). Soma disparos em `disparos` — uma
     * fatia/regra sem disparo nenhum na bateria de teste é regra não provada (padrão da Fatia 7).
     *
     * Sem escassez (`soma(direta) <= capacidade`): arredonda cada músculo pra inteiro (meio pra
     * cima) e devolve — piso, rebaixamento e sacrifício não entram em jogo.
     *
     * Sob escassez: piso = `min(direta, max(0, piso do nível iniciante − indireto))` por músculo
     * ativo; rebaixa ao piso do papel na ordem declarada até caber; se nem isso couber,
     * sacrifica na mesma ordem; se nem sacrificar tudo de `ORDEM_SACRIFICIO` bastar (hoje: só
     * CHEST/BACK/QUADS/POSTERIOR sobram), o resto do déficit é cortado PROPORCIONALMENTE ao
     * inverso do `PESO_PRIORIDADE` desses músculos — decisão da Fatia 8, `[REGRA]` — reduzindo-os
     * (e removendo-os, se o corte exigir mais do que eles têm) em vez de deixá-los intocados; o
     * que sobra da capacidade depois disso é excedente proporcional ao peso de prioridade;
     * arredondamento final pra inteiro — piso pra todos, e a série que sobra vai pro mais
     * prioritário com fração, na ordem de prioridade. Nada é podado depois disto.
     *
     * **Garante `soma(resultado) <= capacidade`** desde a Fatia 8 — antes dela, os músculos fora
     * de `ORDEM_SACRIFICIO` nunca eram tocados e podiam sozinhos exceder a capacidade; agora o
     * corte proporcional os alcança também. Mesmo comportamento do oráculo Python
     * (`tools/motor_sim/motor_v3.py::alocar()`).
     */
    fun alocar(
        direta: Map<TargetMuscle, Double>,
        indireto: Map<TargetMuscle, Double>,
        treinaveis: Collection<TargetMuscle>,
        capacidade: Double,
        disparos: MutableMap<String, Int> = mutableMapOf(),
    ): Map<TargetMuscle, Int> {
        val prioridade = treinaveis.sortedWith(
            compareByDescending<TargetMuscle> { PESO_PRIORIDADE.getValue(it) }.thenBy { it.ordinal }
        )

        if (treinaveis.sumOf { direta[it] ?: 0.0 } <= capacidade) {
            return prioridade.mapNotNull { m ->
                val s = arredondar(direta[m] ?: 0.0)
                if (s > 0) m to s else null
            }.toMap()
        }

        disparos["escassez"] = (disparos["escassez"] ?: 0) + 1
        val ativos = prioridade.filter { (direta[it] ?: 0.0) > 0.0 }
        val piso = ativos.associateWith { m ->
            val pisoIniciante = maxOf(0.0, VolumeTargets.piso(m) - (indireto[m] ?: 0.0))
            minOf(direta.getValue(m), pisoIniciante)
        }.toMutableMap()

        for (m in ORDEM_SACRIFICIO) {
            if (piso.values.sum() <= capacidade) break
            val atual = piso[m] ?: continue
            val pisoPapel = pisoDoPapel(m).toDouble()
            if (atual > pisoPapel) {
                piso[m] = pisoPapel
                disparos["rebaixamento"] = (disparos["rebaixamento"] ?: 0) + 1
            }
        }
        for (m in ORDEM_SACRIFICIO) {
            if (piso.values.sum() <= capacidade) break
            if (piso.remove(m) != null) {
                disparos["sacrificio"] = (disparos["sacrificio"] ?: 0) + 1
            }
        }

        // --- corte proporcional (Fatia 8, `[REGRA]`, 2026-10-24): quando sacrificar tudo que
        // está em `ORDEM_SACRIFICIO` ainda não basta (hoje: só CHEST/BACK/QUADS/POSTERIOR
        // sobram), o resto é reduzido PROPORCIONALMENTE ao inverso do `PESO_PRIORIDADE` — quem
        // pesa mais perde menos — em vez de continuar sacrificando um músculo inteiro por vez.
        // Decisão da Fatia 8, medida nos 720 perfis antes de decidir
        // (`docs/historico/motor-v3-fatia-8.md`): o corte sequencial (estender
        // `ORDEM_SACRIFICIO`) deixava os quatro perderem 25–36% de volume; o proporcional erra
        // bem menos. Espelha `tools/motor_sim/motor_v3.py::alocar()` termo a termo.
        if (piso.values.sum() > capacidade && piso.isNotEmpty()) {
            var deficit = piso.values.sum() - capacidade
            val ativosRestantes = piso.keys.toMutableSet()
            for (iteracao in 0 until 100) {
                if (deficit <= EPS || ativosRestantes.isEmpty()) break
                val inv = ativosRestantes.associateWith { 1.0 / PESO_PRIORIDADE.getValue(it) }
                val somaInv = inv.values.sum()
                val cortes = ativosRestantes.associateWith { deficit * inv.getValue(it) / somaInv }
                val estourou = ativosRestantes.filter { cortes.getValue(it) >= piso.getValue(it) - EPS }
                if (estourou.isEmpty()) {
                    for (m in ativosRestantes) {
                        piso[m] = piso.getValue(m) - cortes.getValue(m)
                    }
                    disparos["corte_proporcional"] = (disparos["corte_proporcional"] ?: 0) + 1
                    deficit = 0.0
                    break
                }
                for (m in estourou) {
                    deficit -= piso.getValue(m)
                    piso.remove(m)
                    ativosRestantes.remove(m)
                    disparos["sacrificio_proporcional"] = (disparos["sacrificio_proporcional"] ?: 0) + 1
                }
            }
        }

        val alocado = piso.toMutableMap()
        var sobra = capacidade - alocado.values.sum()
        for (iteracao in 0 until 100) {
            val famintos = alocado.keys.filter { alocado.getValue(it) < direta.getValue(it) - EPS }
            if (sobra <= EPS || famintos.isEmpty()) break
            val pesoTotal = famintos.sumOf { PESO_PRIORIDADE.getValue(it) }
            var movido = 0.0
            for (m in famintos) {
                val add = minOf(sobra * PESO_PRIORIDADE.getValue(m) / pesoTotal, direta.getValue(m) - alocado.getValue(m))
                alocado[m] = alocado.getValue(m) + add
                movido += add
            }
            sobra -= movido
            if (movido < EPS) break
        }

        val inteiro = alocado.mapValues { (_, v) -> floor(v + EPS).toInt() }.toMutableMap()
        var livre = floor(capacidade + EPS).toInt() - inteiro.values.sum()
        for (m in prioridade) {
            if (livre <= 0) break
            if (m !in alocado) continue
            if (alocado.getValue(m) - inteiro.getValue(m) > EPS) {
                inteiro[m] = inteiro.getValue(m) + 1
                livre -= 1
            }
        }
        return inteiro.filterValues { it > 0 }
    }

    /** Kotlin `roundToInt`: meio pra cima — mesma convenção do oráculo Python. */
    private fun arredondar(x: Double): Int = floor(x + 0.5).toInt()
}
