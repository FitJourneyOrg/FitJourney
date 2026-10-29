package dev.rafael.server.features.exercise.engine.v3

import dev.rafael.contract.profile.Goal
import dev.rafael.contract.profile.Level
import dev.rafael.server.features.exercise.engine.TargetMuscle
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Motor v3, Fatia 1 (`docs/trabalho/motor-v3-fatia-1.md`) — prova que a tabela de volume e a de
 * indireto reproduzem os números da especificação (`propostas/motor-v3-ESPECIFICACAO.md`):
 * os totais **65·113·148** por nível, e a demanda direta **94,0·122,0**
 * (intermediário/avançado). Os valores antigos (61·111·148 e 86,0·113,0) valiam até 2026-10-27,
 * quando três coeficientes sem fonte saíram da `VolumeIndirectTable` e o glúteo foi para
 * 8/10/12 — ver o `[REGRA]` da convenção total na especificação.
 */
class VolumeTargetsTest {

    @Test
    fun `alvo semanal soma 65 no iniciante`() {
        assertEquals(65.0, totalAlvo(Level.BEGINNER, Goal.GAIN_MUSCLE))
    }

    @Test
    fun `alvo semanal soma 113 no intermediario`() {
        assertEquals(113.0, totalAlvo(Level.INTERMEDIATE, Goal.GAIN_MUSCLE))
    }

    @Test
    fun `alvo semanal soma 148 no avancado`() {
        assertEquals(148.0, totalAlvo(Level.ADVANCED, Goal.GAIN_MUSCLE))
    }

    @Test
    fun `braco sobe a 12 no intermediario, nao fica em 3`() {
        assertEquals(12.0, VolumeTargets.weeklySets(TargetMuscle.BICEPS, Level.INTERMEDIATE, Goal.GAIN_MUSCLE))
        assertEquals(12.0, VolumeTargets.weeklySets(TargetMuscle.TRICEPS, Level.INTERMEDIATE, Goal.GAIN_MUSCLE))
    }

    @Test
    fun `manter aplica o fator 0,6 ao alvo semanal, nao so ao teto da sessao`() {
        // Regressão do E10 (2026-10-27): o fator de objetivo existia só em
        // `CapacityModel.fatorObjetivo` (teto da sessão) e faltava aqui, no alvo semanal. Todo
        // perfil MAINTAIN recebia alvo 67% maior que o do oráculo — 180 dos 720 perfis. Nenhum
        // teste exercitava MAINTAIN no alvo, e é por isso que passou.
        assertEquals(0.6, VolumeTargets.fatorObjetivoAlvo(Goal.MAINTAIN))
        assertEquals(1.0, VolumeTargets.fatorObjetivoAlvo(Goal.GAIN_MUSCLE))

        assertEquals(39.0, totalAlvo(Level.BEGINNER, Goal.MAINTAIN), absoluteTolerance = 1e-9)
        assertEquals(67.8, totalAlvo(Level.INTERMEDIATE, Goal.MAINTAIN), absoluteTolerance = 1e-9)
        assertEquals(88.8, totalAlvo(Level.ADVANCED, Goal.MAINTAIN), absoluteTolerance = 1e-9)
    }

    @Test
    fun `o minimo efetivo de quem mantem e o MV, nao o MEV`() {
        // Peito: MEV 8 (coluna do iniciante), MV 4,8. Sem esta distinção, todo MAINTAIN/BEGINNER
        // reprovava na auditoria por design — 660 pares nos 720 perfis.
        assertEquals(8.0, VolumeTargets.minimoEfetivo(TargetMuscle.CHEST, Goal.GAIN_MUSCLE))
        assertEquals(4.8, VolumeTargets.minimoEfetivo(TargetMuscle.CHEST, Goal.MAINTAIN), absoluteTolerance = 1e-9)
        // caminho de falha: saúde geral NÃO é manutenção — usa o MEV cheio
        assertEquals(8.0, VolumeTargets.minimoEfetivo(TargetMuscle.CHEST, Goal.GENERAL_HEALTH))
    }

    @Test
    fun `saude geral usa o piso mesmo no avancado`() {
        assertEquals(
            VolumeTargets.weeklySets(TargetMuscle.BACK, Level.BEGINNER, Goal.GAIN_MUSCLE),
            VolumeTargets.weeklySets(TargetMuscle.BACK, Level.ADVANCED, Goal.GENERAL_HEALTH),
        )
    }

    @Test
    fun `demanda direta do intermediario reproduz a tabela da especificacao`() {
        val resultado = demandaDiretaPorMusculo(ALVO_INTERMEDIARIO, COMPOSTO_INTERMEDIARIO)

        assertEquals(14.0, resultado.getValue(TargetMuscle.CHEST))
        assertEquals(16.0, resultado.getValue(TargetMuscle.BACK))
        assertEquals(12.0, resultado.getValue(TargetMuscle.SHOULDERS))
        assertEquals(8.0, resultado.getValue(TargetMuscle.BICEPS))
        assertEquals(4.0, resultado.getValue(TargetMuscle.TRICEPS))
        // Antebraço deixou de receber indireto em 2026-10-27 (os dois pares `→ antebraço`
        // saíram por falta de fonte): a demanda direta dele virou o alvo cheio, 0 → 6.
        assertEquals(6.0, resultado.getValue(TargetMuscle.FOREARMS))
        assertEquals(12.0, resultado.getValue(TargetMuscle.QUADS))
        assertEquals(10.0, resultado.getValue(TargetMuscle.POSTERIOR))
        assertEquals(9.0, resultado.getValue(TargetMuscle.CALVES))
        // Glúteo: alvo 10 (era 8) − indireto 7 = 3 (era 1).
        assertEquals(3.0, resultado.getValue(TargetMuscle.GLUTES))
        assertEquals(94.0, resultado.values.sum())
    }

    @Test
    fun `demanda direta do avancado reproduz a tabela da especificacao`() {
        val resultado = demandaDiretaPorMusculo(ALVO_AVANCADO, COMPOSTO_AVANCADO)

        assertEquals(18.0, resultado.getValue(TargetMuscle.CHEST))
        assertEquals(20.0, resultado.getValue(TargetMuscle.BACK))
        assertEquals(16.0, resultado.getValue(TargetMuscle.SHOULDERS))
        assertEquals(7.0, resultado.getValue(TargetMuscle.BICEPS))
        assertEquals(6.0, resultado.getValue(TargetMuscle.TRICEPS))
        assertEquals(9.0, resultado.getValue(TargetMuscle.FOREARMS))
        assertEquals(16.0, resultado.getValue(TargetMuscle.QUADS))
        assertEquals(14.0, resultado.getValue(TargetMuscle.POSTERIOR))
        assertEquals(12.0, resultado.getValue(TargetMuscle.CALVES))
        assertEquals(4.0, resultado.getValue(TargetMuscle.GLUTES))
        assertEquals(122.0, resultado.values.sum())
    }

    @Test
    fun `ombro nao e descontado por nenhum doador`() {
        // Supino dá trabalho real ao deltoide anterior, mas descontar subserviria lateral e
        // posterior — por isso SHOULDERS nunca aparece como recipiente.
        val indireto = VolumeIndirectTable.indiretoRecebido(COMPOSTO_INTERMEDIARIO)
        assertEquals(null, indireto[TargetMuscle.SHOULDERS])
    }

    @Test
    fun `nenhum coeficiente sem fonte voltou para a tabela`() {
        // Tripwire do `[INV]` de 2026-10-27: coeficiente sem fonte não volta. Este teste provava
        // que glúteo creditava 0,2 ao posterior; o par saiu por falta de fonte, junto com os dois
        // `→ antebraço`. Se alguém devolver qualquer um dos três, isto falha.
        val doGluteo = VolumeIndirectTable.indiretoRecebido(mapOf(TargetMuscle.GLUTES to 10.0))
        assertEquals(null, doGluteo[TargetMuscle.POSTERIOR], "glúteo → posterior voltou?")

        val doDorso = VolumeIndirectTable.indiretoRecebido(
            mapOf(TargetMuscle.BACK to 10.0, TargetMuscle.POSTERIOR to 10.0),
        )
        assertEquals(null, doDorso[TargetMuscle.FOREARMS], "→ antebraço voltou?")
        // e o que sobrevive continua creditando, com fonte
        assertEquals(5.0, doDorso.getValue(TargetMuscle.BICEPS))
        assertEquals(5.0, doDorso.getValue(TargetMuscle.GLUTES))
    }

    @Test
    fun `demanda direta nunca fica negativa quando indireto excede o alvo`() {
        // Era o antebraço no intermediário (alvo 6, indireto 7) até 2026-10-27, quando os dois
        // pares `→ antebraço` saíram por falta de fonte. Hoje nenhum músculo da tabela cai neste
        // caso, mas a função tem de continuar recusando negativo — é a guarda da subtração.
        assertEquals(0.0, demandaDireta(alvo = 6.0, indireto = 7.0))
    }

    @Test
    fun `piso direto so existe nos tres musculos com credito indireto sancionado`() {
        for (nivel in listOf(Level.INTERMEDIATE, Level.ADVANCED)) {
            for (m in VolumeTargets.PISO_DIRETO_MUSCULOS) {
                assertEquals(2, VolumeTargets.pisoDireto(m, nivel), "$m/$nivel")
            }
        }
        // Caminho de FALHA, nos dois eixos: músculo que não recebe crédito indireto não tem piso
        // direto (nada a compensar), e o iniciante é isento em todos (regra geral do híbrido —
        // ele cresce com o composto e é quem tem o orçamento mais apertado).
        assertEquals(0, VolumeTargets.pisoDireto(TargetMuscle.CHEST, Level.ADVANCED))
        assertEquals(0, VolumeTargets.pisoDireto(TargetMuscle.FOREARMS, Level.ADVANCED))
        for (m in VolumeTargets.PISO_DIRETO_MUSCULOS) {
            assertEquals(0, VolumeTargets.pisoDireto(m, Level.BEGINNER), "isenção do iniciante: $m")
        }
    }

    @Test
    fun `os musculos do piso direto sao exatamente os que recebem credito indireto`() {
        // `[INV]`: o piso direto existe PARA compensar o crédito indireto — se um par novo entrar
        // na tabela de indireto, o recebedor novo precisa entrar aqui (ou a exclusão precisa ser
        // decidida e registrada). Este teste é o que força essa conversa.
        assertEquals(VolumeIndirectTable.recebedoresDeCredito, VolumeTargets.PISO_DIRETO_MUSCULOS)
    }

    private fun totalAlvo(nivel: Level, objetivo: Goal): Double =
        TargetMuscle.entries.sumOf { VolumeTargets.weeklySets(it, nivel, objetivo) }

    companion object {
        // Alvos semanais (especificação, tabela "Volume semanal por músculo").
        private val ALVO_INTERMEDIARIO = mapOf(
            TargetMuscle.CHEST to 14.0, TargetMuscle.BACK to 16.0, TargetMuscle.SHOULDERS to 12.0,
            TargetMuscle.BICEPS to 12.0, TargetMuscle.TRICEPS to 12.0, TargetMuscle.FOREARMS to 6.0,
            TargetMuscle.QUADS to 12.0, TargetMuscle.POSTERIOR to 10.0, TargetMuscle.CALVES to 9.0,
            TargetMuscle.GLUTES to 10.0,
        )
        private val ALVO_AVANCADO = mapOf(
            TargetMuscle.CHEST to 18.0, TargetMuscle.BACK to 20.0, TargetMuscle.SHOULDERS to 16.0,
            TargetMuscle.BICEPS to 15.0, TargetMuscle.TRICEPS to 16.0, TargetMuscle.FOREARMS to 9.0,
            TargetMuscle.QUADS to 16.0, TargetMuscle.POSTERIOR to 14.0, TargetMuscle.CALVES to 12.0,
            TargetMuscle.GLUTES to 12.0,
        )

        // Séries de composto por semana de cada doador (especificação, "Como a demanda direta
        // foi recalculada", passo 1 — produzido pela regra de composição, Fatia 5).
        private val COMPOSTO_INTERMEDIARIO = mapOf(
            TargetMuscle.CHEST to 8.0, TargetMuscle.BACK to 8.0, TargetMuscle.SHOULDERS to 8.0,
            TargetMuscle.QUADS to 8.0, TargetMuscle.POSTERIOR to 6.0, TargetMuscle.GLUTES to 0.0,
        )
        private val COMPOSTO_AVANCADO = mapOf(
            TargetMuscle.CHEST to 14.0, TargetMuscle.BACK to 16.0, TargetMuscle.SHOULDERS to 6.0,
            TargetMuscle.QUADS to 8.0, TargetMuscle.POSTERIOR to 8.0, TargetMuscle.GLUTES to 0.0,
        )
    }
}
