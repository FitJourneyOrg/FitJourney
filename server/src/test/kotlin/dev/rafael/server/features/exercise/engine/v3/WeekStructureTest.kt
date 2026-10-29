package dev.rafael.server.features.exercise.engine.v3

import dev.rafael.server.features.exercise.engine.TargetMuscle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Motor v3, Fatia 4 (`docs/trabalho/motor-v3-fatia-4.md`) — prova `WeekStructure` contra
 * `server/src/test/oracle/motor_v3.py::montar_semana()`, usando como entrada as mesmas saídas REAIS de
 * `PriorityAllocation.alocar()` da Fatia 3 (não números inventados).
 */
class WeekStructureTest {

    private fun diasDe(n: Int) = WeekStructure.divisao(n).second

    @Test
    fun `divisao cobre 2 a 6 dias, fora disso falha alto`() {
        assertEquals("FULL_BODY", WeekStructure.divisao(2).first)
        assertEquals("FULL_BODY", WeekStructure.divisao(3).first)
        assertEquals("UPPER_LOWER", WeekStructure.divisao(4).first)
        assertEquals("UL_PPL", WeekStructure.divisao(5).first)
        assertEquals("PPL", WeekStructure.divisao(6).first)
        assertFailsWith<IllegalArgumentException> { WeekStructure.divisao(1) }
        assertFailsWith<IllegalArgumentException> { WeekStructure.divisao(7) }
    }

    @Test
    fun `sem escassez distribui exatamente como o python, sem disparo`() {
        // Fixture A da Fatia 3: alocado sem escassez (capacidade 200).
        val alocado = mapOf(
            TargetMuscle.BACK to 20, TargetMuscle.CHEST to 18, TargetMuscle.QUADS to 16,
            TargetMuscle.SHOULDERS to 16, TargetMuscle.POSTERIOR to 14, TargetMuscle.GLUTES to 4,
            TargetMuscle.TRICEPS to 6, TargetMuscle.BICEPS to 7, TargetMuscle.CALVES to 12,
        )
        val disparos = mutableMapOf<String, Int>()
        val semana = WeekStructure.distribuirPelosDias(alocado, diasDe(4), disparos)

        assertEquals(
            listOf(
                mapOf(TargetMuscle.BACK to 10, TargetMuscle.CHEST to 9, TargetMuscle.SHOULDERS to 8, TargetMuscle.TRICEPS to 3, TargetMuscle.BICEPS to 4),
                mapOf(TargetMuscle.QUADS to 8, TargetMuscle.POSTERIOR to 7, TargetMuscle.CALVES to 6),
                mapOf(TargetMuscle.BACK to 10, TargetMuscle.CHEST to 9, TargetMuscle.SHOULDERS to 8, TargetMuscle.TRICEPS to 3, TargetMuscle.BICEPS to 3),
                mapOf(TargetMuscle.QUADS to 8, TargetMuscle.POSTERIOR to 7, TargetMuscle.GLUTES to 4, TargetMuscle.CALVES to 6),
            ),
            semana,
        )
        assertEquals(emptyMap(), disparos)
    }

    @Test
    fun `escassez com um rebaixamento (capacidade 40) distribui exatamente como o python`() {
        // Fixture B da Fatia 3: alocado com 1 rebaixamento, sem sacrifício.
        val alocado = mapOf(
            TargetMuscle.BACK to 11, TargetMuscle.CHEST to 9, TargetMuscle.QUADS to 6,
            TargetMuscle.SHOULDERS to 6, TargetMuscle.POSTERIOR to 6, TargetMuscle.CALVES to 2,
        )
        val disparos = mutableMapOf<String, Int>()
        val semana = WeekStructure.distribuirPelosDias(alocado, diasDe(4), disparos)

        assertEquals(
            listOf(
                mapOf(TargetMuscle.BACK to 6, TargetMuscle.CHEST to 5, TargetMuscle.SHOULDERS to 3),
                mapOf(TargetMuscle.QUADS to 3, TargetMuscle.POSTERIOR to 3, TargetMuscle.CALVES to 2),
                mapOf(TargetMuscle.BACK to 5, TargetMuscle.CHEST to 4, TargetMuscle.SHOULDERS to 3),
                mapOf(TargetMuscle.QUADS to 3, TargetMuscle.POSTERIOR to 3),
            ),
            semana,
        )
        assertEquals(emptyMap(), disparos)
    }

    @Test
    fun `total abaixo do piso prescritivel nao chega mais nesta etapa - vira INV que falha alto`() {
        // Era a fixture C da Fatia 3: TRICEPS=1 e BICEPS=1 SUBIAM ao piso aqui, por fora do
        // orçamento que a etapa [3] já havia fechado — e era essa a causa de
        // `entregue > capacidade` em 167/720 perfis. A decisão migrou inteira para a etapa [3]
        // (`PriorityAllocation.pisoPrescritivel`), que sobe DENTRO do orçamento ou zera o músculo.
        // Esta etapa não corrige mais nada: ela denuncia quem passou.
        val alocado = mapOf(
            TargetMuscle.BACK to 14, TargetMuscle.CHEST to 12, TargetMuscle.QUADS to 10,
            TargetMuscle.SHOULDERS to 10, TargetMuscle.POSTERIOR to 9, TargetMuscle.CALVES to 6,
            TargetMuscle.TRICEPS to 1, TargetMuscle.BICEPS to 1, TargetMuscle.GLUTES to 2,
        )
        val erro = assertFailsWith<IllegalArgumentException> {
            WeekStructure.distribuirPelosDias(alocado, diasDe(4), mutableMapOf())
        }
        assertTrue("[INV]" in erro.message!!, erro.message)
        assertTrue("TRICEPS" in erro.message!!, erro.message)
    }

    @Test
    fun `a mesma fixture, saneada pela etapa 3, distribui sem tocar em nada`() {
        // O caminho feliz do teste acima: com TRICEPS e BICEPS já no piso (2), que é o que a
        // etapa [3] entrega hoje, esta etapa não dispara nada.
        val alocado = mapOf(
            TargetMuscle.BACK to 14, TargetMuscle.CHEST to 12, TargetMuscle.QUADS to 10,
            TargetMuscle.SHOULDERS to 10, TargetMuscle.POSTERIOR to 9, TargetMuscle.CALVES to 6,
            TargetMuscle.TRICEPS to 2, TargetMuscle.BICEPS to 2, TargetMuscle.GLUTES to 2,
        )
        val disparos = mutableMapOf<String, Int>()
        val semana = WeekStructure.distribuirPelosDias(alocado, diasDe(4), disparos)

        assertEquals(
            listOf(
                mapOf(TargetMuscle.BACK to 7, TargetMuscle.CHEST to 6, TargetMuscle.SHOULDERS to 5, TargetMuscle.TRICEPS to 2),
                mapOf(TargetMuscle.QUADS to 5, TargetMuscle.POSTERIOR to 5, TargetMuscle.CALVES to 3),
                mapOf(TargetMuscle.BACK to 7, TargetMuscle.CHEST to 6, TargetMuscle.SHOULDERS to 5, TargetMuscle.BICEPS to 2),
                mapOf(TargetMuscle.QUADS to 5, TargetMuscle.POSTERIOR to 4, TargetMuscle.CALVES to 3, TargetMuscle.GLUTES to 2),
            ),
            semana,
        )
        assertEquals(emptyMap(), disparos)
    }

    @Test
    fun `GLUTES com 4 series e piso 3 treina 1 vez por semana, nao 2 - conformidade com o oraculo`() {
        // Especificação + oráculo: músculo com poucas séries treina em menos dias, não fatiado
        // abaixo do piso do papel (3 no composto). GLUTES não é SO_ISOLAMENTO (tem composto
        // próprio no catálogo), então 4 // 3 = 1 treino/semana com as 4 séries juntas.
        //
        // É este exato caso que causa os 2 vermelhos do diferencial contra `modelo_a_mao.py`
        // (que assume frequência fixa 2×) — a pergunta de qual lado está certo continua aberta
        // na Fatia 9. Este teste prova só que o Kotlin faz o que o oráculo faz, não resolve isso.
        val disparos = mutableMapOf<String, Int>()
        val semana = WeekStructure.distribuirPelosDias(mapOf(TargetMuscle.GLUTES to 4), diasDe(4), disparos)

        assertEquals(
            listOf(emptyMap(), emptyMap(), emptyMap(), mapOf(TargetMuscle.GLUTES to 4)),
            semana,
            "GLUTES treina só 1x/semana (Lower B), as 4 séries juntas — não 2x2",
        )
        assertEquals(emptyMap(), disparos)
    }

    @Test
    fun `espalhar devolve todos os dias quando freq cobre todos`() {
        assertEquals(listOf(1, 3), WeekStructure.espalhar(listOf(1, 3), freq = 2, desloc = 0))
    }

    @Test
    fun `espalhar com freq 1 escolhe o dia pelo deslocamento do musculo no enum`() {
        // Mesmo cálculo do caso GLUTES isolado: dias_m=[1,3], desloc=GLUTES.ordinal=9 -> dia 3.
        assertEquals(listOf(3), WeekStructure.espalhar(listOf(1, 3), freq = 1, desloc = TargetMuscle.GLUTES.ordinal))
    }

    @Test
    fun `alocado acima da capacidade e processado como vem, nao e cortado aqui`() {
        // Achado da auditoria da Fatia 3: 21,4% dos perfis excedem a capacidade. Esta fatia não
        // corta nada — só distribui o que recebeu. Fixture: capacidade 29,33, soma 30.
        val alocado = mapOf(
            TargetMuscle.BACK to 10, TargetMuscle.CHEST to 8, TargetMuscle.QUADS to 6, TargetMuscle.POSTERIOR to 6,
        )
        val semana = WeekStructure.distribuirPelosDias(alocado, diasDe(4))
        val somaDistribuida = semana.sumOf { dia -> dia.values.sum() }
        assertTrue(somaDistribuida == alocado.values.sum(), "nada deve ser perdido nem ganho na distribuição")
        assertEquals(30, somaDistribuida)
    }
}
