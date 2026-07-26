package dev.rafael.server.features.exercise.engine

import dev.rafael.contract.profile.Goal
import dev.rafael.contract.profile.Level
import dev.rafael.contract.profile.MuscleGroup
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Testa o modelo de volume/prescrição (ARCH #27/#28) — puro, sem banco.
 * RoleParams: reps/descanso/RIR por papel. VolumeTable: séries/semana por músculo e foco.
 */
class RoleParamsTest {

    // ---------- RoleParams ----------

    @Test
    fun `composto pesado usa 5-8 e RIR 2 no intermediario`() {
        val p = RoleParams.paramsFor(SlotRole.COMPOSTO_PESADO, Level.INTERMEDIATE, Goal.GAIN_MUSCLE)
        assertEquals("5-8", p.repRange)
        assertEquals(150, p.restSeconds)
        assertEquals(2, p.rir)
    }

    @Test
    fun `isolamento nao passa de 15 reps (fix ARCH #28)`() {
        val p = RoleParams.paramsFor(SlotRole.ISOLAMENTO, Level.INTERMEDIATE, Goal.GAIN_MUSCLE)
        assertEquals("12-15", p.repRange)
        assertEquals(75, p.restSeconds)
        assertEquals(1, p.rir)
    }

    @Test
    fun `acessorio fica em 8-12`() {
        val p = RoleParams.paramsFor(SlotRole.COMPOSTO_ACESSORIO, Level.INTERMEDIATE, Goal.GAIN_MUSCLE)
        assertEquals("8-12", p.repRange)
    }

    @Test
    fun `iniciante usa RIR-piso 3 (conservador)`() {
        val p = RoleParams.paramsFor(SlotRole.COMPOSTO_PESADO, Level.BEGINNER, Goal.GAIN_MUSCLE)
        assertEquals(3, p.rir, "iniciante nunca falha: RIR 3")
    }

    @Test
    fun `general health e conservador mesmo em avancado`() {
        val p = RoleParams.paramsFor(SlotRole.ISOLAMENTO, Level.ADVANCED, Goal.GENERAL_HEALTH)
        assertEquals(3, p.rir, "saúde geral usa cue conservador")
    }

    // ---------- VolumeTable ----------

    @Test
    fun `volume semanal escala com o nivel`() {
        val beg = VolumeTable.weeklySets(TargetMuscle.CHEST, Level.BEGINNER, Goal.GAIN_MUSCLE)
        val inter = VolumeTable.weeklySets(TargetMuscle.CHEST, Level.INTERMEDIATE, Goal.GAIN_MUSCLE)
        val adv = VolumeTable.weeklySets(TargetMuscle.CHEST, Level.ADVANCED, Goal.GAIN_MUSCLE)
        assertTrue(beg < inter && inter < adv, "iniciante < intermediário < avançado ($beg<$inter<$adv)")
    }

    @Test
    fun `general health usa o piso (MEV) independente do nivel`() {
        val piso = VolumeTable.weeklySets(TargetMuscle.CHEST, Level.BEGINNER, Goal.GAIN_MUSCLE)
        val adv = VolumeTable.weeklySets(TargetMuscle.CHEST, Level.ADVANCED, Goal.GENERAL_HEALTH)
        assertEquals(piso, adv, "saúde geral fica no piso mesmo se avançado")
    }

    @Test
    fun `mrv e o teto por musculo`() {
        assertTrue(
            VolumeTable.mrv(TargetMuscle.CHEST) >=
                VolumeTable.weeklySets(TargetMuscle.CHEST, Level.ADVANCED, Goal.GAIN_MUSCLE),
            "MRV >= volume do avançado",
        )
    }

    @Test
    fun `foco de pernas abre em quadriceps posterior e panturrilha`() {
        assertEquals(
            setOf(TargetMuscle.QUADS, TargetMuscle.POSTERIOR, TargetMuscle.CALVES),
            VolumeTable.targetsForFocus(MuscleGroup.LEGS),
        )
    }

    @Test
    fun `foco de peito mapeia so pra CHEST`() {
        assertEquals(setOf(TargetMuscle.CHEST), VolumeTable.targetsForFocus(MuscleGroup.CHEST))
    }
}
