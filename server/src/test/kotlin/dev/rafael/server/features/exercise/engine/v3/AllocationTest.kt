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

    // ----------------------------------------------------------------- piso prescritível (F8)

    @Test
    fun `piso prescritivel zera quem nao cabe no orcamento em vez de estourar`() {
        // Caminho de FALHA do piso: orçamento de 6 com CHEST em 4 e dois pendentes de 1. CHEST
        // está decidido (>= piso), BICEPS sobe a 2 e fecha os 6; FOREARMS não cabe e é ZERADO.
        val disparos = mutableMapOf<String, Int>()
        val saida = PriorityAllocation.pisoPrescritivel(
            mapOf(TargetMuscle.CHEST to 4, TargetMuscle.BICEPS to 1, TargetMuscle.FOREARMS to 1),
            listOf(TargetMuscle.CHEST, TargetMuscle.BICEPS, TargetMuscle.FOREARMS),
            6.0, disparos,
        )
        assertEquals(mapOf(TargetMuscle.CHEST to 4, TargetMuscle.BICEPS to 2), saida)
        assertEquals(1, disparos["sobe_ao_piso_dentro_do_orcamento"])
        assertEquals(1, disparos["zerado_por_nao_caber_no_piso"])
    }

    @Test
    fun `piso prescritivel nao inverte a prioridade - o gluteo nao perde a vaga para o triceps`() {
        // Regressão medida (2026-10-27, oráculo): medindo o orçamento contra a FRAÇÃO dos
        // pendentes, a série do tríceps ocupava a vaga na vez do glúteo, o glúteo era zerado e o
        // tríceps subia com o que ele liberou — 18 vezes nos 720, contra a decisão de produto que
        // põe o glúteo (peso 0,7) acima do tríceps (0,55).
        val disparos = mutableMapOf<String, Int>()
        val saida = PriorityAllocation.pisoPrescritivel(
            mapOf(TargetMuscle.QUADS to 6, TargetMuscle.GLUTES to 1, TargetMuscle.TRICEPS to 1),
            listOf(TargetMuscle.QUADS, TargetMuscle.GLUTES, TargetMuscle.TRICEPS),
            8.0, disparos,
        )
        assertEquals(mapOf(TargetMuscle.QUADS to 6, TargetMuscle.GLUTES to 2), saida)
        assertEquals(8, saida.values.sum(), "nunca por fora do orçamento")
    }

    @Test
    fun `piso prescritivel com capacidade infinita sobe todo mundo`() {
        // O perfil de REFERÊNCIA chega com capacidade infinita — `floor(inf).toInt()` não é
        // representável, e o oráculo estourava `OverflowError` no caminho equivalente.
        val disparos = mutableMapOf<String, Int>()
        val saida = PriorityAllocation.pisoPrescritivel(
            mapOf(TargetMuscle.CHEST to 1, TargetMuscle.FOREARMS to 1),
            listOf(TargetMuscle.CHEST, TargetMuscle.FOREARMS),
            Double.POSITIVE_INFINITY, disparos,
        )
        assertEquals(mapOf(TargetMuscle.CHEST to 2, TargetMuscle.FOREARMS to 2), saida)
        assertEquals(null, disparos["zerado_por_nao_caber_no_piso"])
    }

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

        // Fatia 8, piso prescritível (2026-10-27): TRICEPS e BICEPS saíam daqui com 1 série —
        // fração que não se prescreve. Hoje TRICEPS SOBE ao piso do papel (2, só-isolamento)
        // porque a sobra do orçamento paga, e BICEPS, que viria depois e não cabia, é ZERADO.
        // Conferido contra o oráculo com estas mesmas entradas: saída idêntica.
        assertEquals(
            mapOf(
                TargetMuscle.BACK to 14, TargetMuscle.CHEST to 12, TargetMuscle.QUADS to 10,
                TargetMuscle.SHOULDERS to 10, TargetMuscle.POSTERIOR to 9, TargetMuscle.CALVES to 6,
                TargetMuscle.TRICEPS to 2, TargetMuscle.GLUTES to 2,
            ),
            alocado,
        )
        assertEquals(
            mapOf(
                "escassez" to 1,
                "sobe_ao_piso_dentro_do_orcamento" to 1,
                "zerado_por_nao_caber_no_piso" to 1,
            ),
            disparos,
        )
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

        // Fatia 8, piso prescritível (2026-10-27): CHEST sobrava com 1 série e foi ZERADO —
        // subi-lo ao piso do papel (3, tem composto) não caberia nos 5. **A soma cai de 5 para
        // 4: sobra 1 série de capacidade sem uso, deliberadamente.** É o preço declarado da
        // `[REGRA]`: orçamento ocioso é preferível a prescrição que não se executa. Conferido
        // contra o oráculo com estas mesmas entradas: saída idêntica, soma 4.
        assertEquals(mapOf(TargetMuscle.BACK to 4), alocado)
        assertEquals(4, alocado.values.sum())
        assertEquals(
            mapOf(
                "escassez" to 1, "rebaixamento" to 2, "sacrificio" to 5,
                "sacrificio_proporcional" to 2, "corte_proporcional" to 1,
                "zerado_por_nao_caber_no_piso" to 1,
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
