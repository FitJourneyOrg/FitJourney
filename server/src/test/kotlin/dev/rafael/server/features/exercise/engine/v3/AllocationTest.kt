package dev.rafael.server.features.exercise.engine.v3

import dev.rafael.server.features.exercise.engine.TargetMuscle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Motor v3, Fatia 3 (`docs/trabalho/motor-v3-fatia-3.md`) — prova `PriorityAllocation.alocar()`
 * contra `tools/motor_sim/motor_v3.py::alocar()`, com os MESMOS `direta`/`indireto` (avançado,
 * especificação) em cinco capacidades. Os cinco resultados abaixo são a saída exata do Python
 * para essas entradas — sem tolerância, porque agora são inteiros.
 */
class AllocationTest {

    // ENTRADAS HISTÓRICAS, não a contabilidade de hoje: eram alvo avançado − indireto avançado
    // da Fatia 1, quando o antebraço recebia 12 de indireto e ficava com 0 de demanda direta.
    // Depois de 2026-10-27 (pares `→ antebraço` removidos por falta de fonte) esse estado não
    // acontece mais. Mantidas de propósito: são entradas VÁLIDAS de `alocar()`, e o que este
    // arquivo testa é o comportamento dele sob escassez (ordem de sacrifício, rebaixamento,
    // corte proporcional) — refazer o cenário mudaria todos os esperados sem testar mais nada.
    // Débito registrado em `fontes/debitos.md`: atualizar o cenário quando o piso direto entrar.
    private val direta = mapOf(
        TargetMuscle.CHEST to 18.0, TargetMuscle.BACK to 20.0, TargetMuscle.SHOULDERS to 16.0,
        TargetMuscle.BICEPS to 7.0, TargetMuscle.TRICEPS to 6.0, TargetMuscle.FOREARMS to 0.0,
        TargetMuscle.QUADS to 16.0, TargetMuscle.POSTERIOR to 14.0, TargetMuscle.CALVES to 12.0,
        TargetMuscle.GLUTES to 4.0,
    )
    private val indireto = mapOf(
        TargetMuscle.BICEPS to 8.0, TargetMuscle.TRICEPS to 10.0, TargetMuscle.FOREARMS to 12.0,
        TargetMuscle.GLUTES to 8.0,
    )
    private val treinaveis = TargetMuscle.entries.toList()

    @Test
    fun `sem escassez devolve direta arredondada, sem disparo nenhum`() {
        val disparos = mutableMapOf<String, Int>()
        val alocado = PriorityAllocation.alocar(direta, indireto, treinaveis, capacidade = 200.0, disparos)

        assertEquals(
            mapOf(
                TargetMuscle.BACK to 20, TargetMuscle.CHEST to 18, TargetMuscle.QUADS to 16,
                TargetMuscle.SHOULDERS to 16, TargetMuscle.POSTERIOR to 14, TargetMuscle.CALVES to 12,
                TargetMuscle.TRICEPS to 6, TargetMuscle.BICEPS to 7, TargetMuscle.GLUTES to 4,
            ),
            alocado,
        )
        assertEquals(emptyMap(), disparos)
    }

    @Test
    fun `escassez sem rebaixamento usa so o excedente proporcional`() {
        // 4 dias x 60min, avançado (Fatia 2): capacidade 65,33 — cobre 58% da demanda.
        val disparos = mutableMapOf<String, Int>()
        val alocado = PriorityAllocation.alocar(direta, indireto, treinaveis, capacidade = 65.0 + 1.0 / 3, disparos)

        assertEquals(
            mapOf(
                TargetMuscle.BACK to 14, TargetMuscle.CHEST to 12, TargetMuscle.QUADS to 10,
                TargetMuscle.SHOULDERS to 10, TargetMuscle.POSTERIOR to 9, TargetMuscle.CALVES to 6,
                TargetMuscle.TRICEPS to 1, TargetMuscle.BICEPS to 1, TargetMuscle.GLUTES to 2,
            ),
            alocado,
        )
        assertEquals(mapOf("escassez" to 1), disparos)
        assertEquals(65, alocado.values.sum())
    }

    @Test
    fun `escassez com um rebaixamento, sem sacrificio`() {
        val disparos = mutableMapOf<String, Int>()
        val alocado = PriorityAllocation.alocar(direta, indireto, treinaveis, capacidade = 40.0, disparos)

        assertEquals(
            mapOf(
                TargetMuscle.BACK to 11, TargetMuscle.CHEST to 9, TargetMuscle.QUADS to 6,
                TargetMuscle.SHOULDERS to 6, TargetMuscle.POSTERIOR to 6, TargetMuscle.CALVES to 2,
            ),
            alocado,
        )
        assertEquals(mapOf("escassez" to 1, "rebaixamento" to 1), disparos)
        assertEquals(40, alocado.values.sum())
    }

    @Test
    fun `escassez com rebaixamento e sacrificio, na ordem declarada`() {
        val disparos = mutableMapOf<String, Int>()
        val alocado = PriorityAllocation.alocar(direta, indireto, treinaveis, capacidade = 34.0, disparos)

        assertEquals(
            mapOf(
                TargetMuscle.BACK to 11, TargetMuscle.CHEST to 8, TargetMuscle.QUADS to 6,
                TargetMuscle.SHOULDERS to 3, TargetMuscle.POSTERIOR to 6,
            ),
            alocado,
        )
        // CALVES e GLUTES saem antes de SHOULDERS — é a ordem declarada, antebraço-primeiro.
        assertEquals(mapOf("escassez" to 1, "rebaixamento" to 2, "sacrificio" to 1), disparos)
        assertEquals(34, alocado.values.sum())
    }

    @Test
    fun `escassez extrema agora cabe na capacidade, com corte proporcional`() {
        // Fatia 8, `[REGRA]` (2026-10-24): quando sacrificar tudo de ORDEM_SACRIFICIO não basta
        // (hoje: só CHEST/BACK/QUADS/POSTERIOR sobram, somando 33 > 29,33), o resto do déficit é
        // cortado PROPORCIONALMENTE ao inverso do PESO_PRIORIDADE deles, em vez de deixá-los
        // intocados. Antes da Fatia 8 este teste esperava soma 30 (> capacidade); agora soma 29.
        val disparos = mutableMapOf<String, Int>()
        val alocado = PriorityAllocation.alocar(direta, indireto, treinaveis, capacidade = 29.0 + 1.0 / 3, disparos)

        assertEquals(
            mapOf(TargetMuscle.BACK to 10, TargetMuscle.CHEST to 8, TargetMuscle.QUADS to 6, TargetMuscle.POSTERIOR to 5),
            alocado,
        )
        assertEquals(29, alocado.values.sum())
        assertEquals(mapOf("escassez" to 1, "rebaixamento" to 2, "sacrificio" to 5, "corte_proporcional" to 1), disparos)
    }

    @Test
    fun `corte proporcional pode remover um dos 4 intocaveis, se o deficit exigir`() {
        // Fatia 8: capacidade tão pequena que, mesmo cortando CHEST/BACK/QUADS/POSTERIOR
        // proporcionalmente, QUADS e POSTERIOR ainda precisam ser removidos por completo
        // (`sacrificio_proporcional`) antes de BACK/CHEST caberem.
        val disparos = mutableMapOf<String, Int>()
        val alocado = PriorityAllocation.alocar(direta, indireto, treinaveis, capacidade = 5.0, disparos)

        assertEquals(mapOf(TargetMuscle.BACK to 4, TargetMuscle.CHEST to 1), alocado)
        assertEquals(5, alocado.values.sum())
        assertEquals(
            mapOf(
                "escassez" to 1, "rebaixamento" to 2, "sacrificio" to 5,
                "sacrificio_proporcional" to 2, "corte_proporcional" to 1,
            ),
            disparos,
        )
    }

    @Test
    fun `ordem de sacrificio e derivada do peso, nao mais lista fixa`() {
        // Fatia 8, 2026-10-24: GLUTES (peso 0,7) e BICEPS (peso 0,5) trocaram de posicao em
        // relacao a lista antiga -- a ordem agora bate com PESO_PRIORIDADE crescente, por
        // construcao (fonte unica).
        assertEquals(
            listOf(
                TargetMuscle.FOREARMS, TargetMuscle.CALVES, TargetMuscle.BICEPS,
                TargetMuscle.TRICEPS, TargetMuscle.GLUTES, TargetMuscle.SHOULDERS,
            ),
            PriorityAllocation.ORDEM_SACRIFICIO,
        )
    }

    @Test
    fun `invariante -- ORDEM_SACRIFICIO nunca discorda de PESO_PRIORIDADE`() {
        // `[INV]` fonte unica (Fatia 8): a ordem e SEMPRE ascendente em peso, com desempate por
        // ordinal -- nao e so um valor fixo que calha de bater hoje, e uma propriedade que vale
        // para qualquer PESO_PRIORIDADE que o futuro venha a ter.
        val pesos = PriorityAllocation.ORDEM_SACRIFICIO.map { PriorityAllocation.PESO_PRIORIDADE.getValue(it) }
        assertEquals(pesos.sorted(), pesos)
        for (musculo in PriorityAllocation.ORDEM_SACRIFICIO) {
            assertTrue(musculo !in PriorityAllocation.PROTEGIDOS_ALOCACAO)
        }
    }

    @Test
    fun `piso do papel e 2 para musculo so-isolamento e 3 para quem tem composto`() {
        assertEquals(2, PriorityAllocation.pisoDoPapel(TargetMuscle.BICEPS))
        assertEquals(2, PriorityAllocation.pisoDoPapel(TargetMuscle.CALVES))
        assertEquals(3, PriorityAllocation.pisoDoPapel(TargetMuscle.SHOULDERS))
        assertEquals(3, PriorityAllocation.pisoDoPapel(TargetMuscle.GLUTES))
    }

    @Test
    fun `nenhuma regra de escassez fica sem disparo na bateria`() {
        // Padrão da Fatia 7: regra sem disparo nenhum é regra não provada.
        val totais = mutableMapOf<String, Int>()
        for (capacidade in listOf(200.0, 65.0 + 1.0 / 3, 40.0, 34.0, 29.0 + 1.0 / 3, 5.0)) {
            val disparos = mutableMapOf<String, Int>()
            PriorityAllocation.alocar(direta, indireto, treinaveis, capacidade, disparos)
            disparos.forEach { (k, v) -> totais[k] = (totais[k] ?: 0) + v }
        }
        for (regra in listOf(
            "escassez", "rebaixamento", "sacrificio", "corte_proporcional", "sacrificio_proporcional",
        )) {
            assertTrue((totais[regra] ?: 0) > 0, "regra '$regra' nunca disparou na bateria")
        }
    }
}
