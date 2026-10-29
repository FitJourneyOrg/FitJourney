package dev.rafael.server.features.exercise.engine.v3

import dev.rafael.contract.profile.Goal
import dev.rafael.contract.profile.Level
import dev.rafael.contract.profile.MuscleGroup
import dev.rafael.server.features.exercise.engine.TargetMuscle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Motor v3, Fatia 10 — o foco do quiz é SÓ um bônus de alvo (+3 pequeno, +6 grande). A fase de dois
 * mesociclos, o piso nos demais grupos e a proteção na alocação são da Fatia 11. Espelha
 * `FocoDoQuiz` de `test_motor_v3.py`. A grade 960 contra o oráculo está em `MotorV3Varredura720Test`.
 */
class FocoDoQuizTest {

    private fun alvos(
        nivel: Level = Level.INTERMEDIATE, objetivo: Goal = Goal.GAIN_MUSCLE, foco: List<MuscleGroup> = emptyList(),
    ): Map<TargetMuscle, Double> =
        TargetMuscle.entries.associateWith { VolumeTargets.weeklySets(it, nivel, objetivo, foco).toDouble() }

    @Test
    fun `sem foco o alvo e o de antes`() {
        val a = alvos()
        assertEquals(listOf(14.0, 12.0, 12.0), listOf(a.getValue(TargetMuscle.CHEST), a.getValue(TargetMuscle.SHOULDERS), a.getValue(TargetMuscle.BICEPS)))
    }

    @Test
    fun `grande ganha 6, pequeno ganha 3, e o resto vai ao piso (Fatia 11, exceto BACK isento)`() {
        val sem = alvos()
        val com = alvos(foco = listOf(MuscleGroup.CHEST, MuscleGroup.BICEPS))
        assertEquals(6.0, com.getValue(TargetMuscle.CHEST) - sem.getValue(TargetMuscle.CHEST))
        assertEquals(3.0, com.getValue(TargetMuscle.BICEPS) - sem.getValue(TargetMuscle.BICEPS))
        // Fatia 11: fora do foco, o alvo vai ao piso -- exceto BACK, isento porque CHEST está em
        // EMPURRAR ("puxar nunca cai abaixo de empurrar").
        TargetMuscle.entries.filter { it != TargetMuscle.CHEST && it != TargetMuscle.BICEPS }.forEach {
            val esperado = if (it == TargetMuscle.BACK) sem.getValue(it) else minOf(sem.getValue(it), VolumeTargets.piso(it).toDouble())
            assertEquals(esperado, com.getValue(it), "$it")
        }
    }

    @Test
    fun `o bonus respeita o MRV`() {
        val a = alvos(nivel = Level.ADVANCED, foco = listOf(MuscleGroup.CHEST, MuscleGroup.BACK))
        assertEquals(22.0 to 25.0, a.getValue(TargetMuscle.CHEST) to a.getValue(TargetMuscle.BACK))
        assertEquals(12.0, alvos(nivel = Level.ADVANCED, foco = listOf(MuscleGroup.FOREARMS)).getValue(TargetMuscle.FOREARMS))
    }

    @Test
    fun `iniciante e saude geral ignoram o foco`() {
        val foco = listOf(MuscleGroup.CHEST)
        assertEquals(alvos(nivel = Level.BEGINNER), alvos(nivel = Level.BEGINNER, foco = foco))
        assertEquals(alvos(objetivo = Goal.GENERAL_HEALTH), alvos(objetivo = Goal.GENERAL_HEALTH, foco = foco))
        assertEquals(emptyList(), VolumeTargets.focoEfetivo(Level.BEGINNER, Goal.GAIN_MUSCLE, foco))
    }

    @Test
    fun `no maximo dois grupos`() {
        val a = alvos(foco = listOf(MuscleGroup.CHEST, MuscleGroup.BACK, MuscleGroup.SHOULDERS))
        // SHOULDERS é o 3º grupo: cai fora do foco EFETIVO (máx. 2) e, Fatia 11, vai ao piso (6.0).
        assertEquals(listOf(20.0, 22.0, 6.0), listOf(a.getValue(TargetMuscle.CHEST), a.getValue(TargetMuscle.BACK), a.getValue(TargetMuscle.SHOULDERS)))
    }

    @Test
    fun `pernas reparte UM bonus entre quadriceps, posterior e panturrilha, e gluteo e foco proprio`() {
        val sem = alvos()
        val com = alvos(foco = listOf(MuscleGroup.LEGS))
        val pernas = listOf(TargetMuscle.QUADS, TargetMuscle.POSTERIOR, TargetMuscle.CALVES)
        assertEquals(
            mapOf(TargetMuscle.QUADS to 3.0, TargetMuscle.POSTERIOR to 2.0, TargetMuscle.CALVES to 1.0),
            pernas.associateWith { com.getValue(it) - sem.getValue(it) },
        )
        assertEquals(6.0, pernas.sumOf { com.getValue(it) - sem.getValue(it) }, "o bônus não pode ser somado cheio em cada alvo")
        // Fatia 11: GLUTES não é alvo do foco LEGS (tem piso próprio, [alvosDoFoco]), então também
        // vai ao piso -- não fica mais igual a "sem foco".
        assertEquals(8.0, com.getValue(TargetMuscle.GLUTES))
    }

    @Test
    fun `alvo coberto duas vezes nao soma duas vezes`() {
        assertEquals(alvos(foco = listOf(MuscleGroup.CHEST)), alvos(foco = listOf(MuscleGroup.CHEST, MuscleGroup.CHEST)))
    }

    @Test
    fun `grande e derivado do peso com a fronteira declarada, e ombro e gluteo sao grandes`() {
        val pesos = PriorityAllocation.PESO_PRIORIDADE
        assertEquals(
            setOf(TargetMuscle.CHEST, TargetMuscle.BACK, TargetMuscle.SHOULDERS, TargetMuscle.QUADS, TargetMuscle.POSTERIOR, TargetMuscle.GLUTES),
            VolumeTargets.GRANDES,
        )
        assertEquals(TargetMuscle.entries.filter { pesos.getValue(it) >= VolumeTargets.FOCO_FRONTEIRA_GRANDE }.toSet(), VolumeTargets.GRANDES)
        // fronteira entre TRICEPS (0,55) e GLUTES (0,7); ninguém na borda
        assertTrue(0.55 < VolumeTargets.FOCO_FRONTEIRA_GRANDE && VolumeTargets.FOCO_FRONTEIRA_GRANDE < 0.7)
        assertTrue(pesos.values.all { kotlin.math.abs(it - VolumeTargets.FOCO_FRONTEIRA_GRANDE) >= 0.049 })
        // o bônus (antes do MRV) de ombro e glúteo é 6; bíceps é 3
        assertEquals(
            mapOf(TargetMuscle.SHOULDERS to 6, TargetMuscle.GLUTES to 6),
            VolumeTargets.bonusDeFoco(Level.INTERMEDIATE, Goal.GAIN_MUSCLE, listOf(MuscleGroup.SHOULDERS, MuscleGroup.GLUTES)),
        )
        assertEquals(mapOf(TargetMuscle.BICEPS to 3), VolumeTargets.bonusDeFoco(Level.INTERMEDIATE, Goal.GAIN_MUSCLE, listOf(MuscleGroup.BICEPS)))
    }

    @Test
    fun `a distribuicao do bonus soma exato e um alvo so recebe o valor cheio`() {
        val pernas = listOf(TargetMuscle.QUADS, TargetMuscle.POSTERIOR, TargetMuscle.CALVES)
        for (total in listOf(3, 6)) {
            assertEquals(total, VolumeTargets.distribuirBonus(total, pernas).values.sum())
            assertEquals(total, VolumeTargets.distribuirBonus(total, listOf(TargetMuscle.BICEPS, TargetMuscle.TRICEPS)).values.sum())
        }
        assertEquals(mapOf(TargetMuscle.CHEST to 6), VolumeTargets.distribuirBonus(6, listOf(TargetMuscle.CHEST)))
    }

    @Test
    fun `core e vocabulario do usuario sem alvo de volume, entao foco nele nao muda nada`() {
        assertEquals(emptySet(), VolumeTargets.alvosDoFoco(MuscleGroup.CORE))
        assertEquals(alvos(), alvos(foco = listOf(MuscleGroup.CORE)))
        // misturado com um foco real, só o real conta
        assertEquals(alvos(foco = listOf(MuscleGroup.CHEST)), alvos(foco = listOf(MuscleGroup.CORE, MuscleGroup.CHEST)))
        // CORE legado no perfil não gasta uma das 2 vagas de foco
        assertEquals(
            alvos(foco = listOf(MuscleGroup.CHEST, MuscleGroup.BACK)),
            alvos(foco = listOf(MuscleGroup.CORE, MuscleGroup.CHEST, MuscleGroup.BACK)),
        )
        // caminho de falha: o quiz não oferece CORE, mas os demais têm alvo
        assertEquals(listOf(MuscleGroup.CORE), MuscleGroup.entries.filter { !it.temAlvoDeVolume })
        assertTrue(MuscleGroup.entries.filter { it.temAlvoDeVolume }.all { VolumeTargets.alvosDoFoco(it).isNotEmpty() })
    }

    @Test
    fun `grupo desconhecido falha alto no id da matriz`() {
        assertFailsWith<IllegalArgumentException> { Oraculo.entradaDe("GAIN_MUSCLE-INTERMEDIATE-4d-60m-30a-ACADEMIA-fPESCOCO") }
        assertFailsWith<IllegalStateException> { Oraculo.entradaDe("GAIN_MUSCLE-INTERMEDIATE-4d-60m-30a-ACADEMIA-xCHEST") }
    }
}
