package dev.rafael.server.features.exercise.engine

import dev.rafael.contract.profile.TrainingEnvironment
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Testa o mapa equipamento→ambiente (ARCH #28) — puro.
 * Academia vê tudo (sem filtro); Casa restringe a halter + corporal + elástico.
 */
class EquipmentEnvironmentMapTest {

    @Test
    fun `casa restringe a halter corporal e elastico`() {
        assertEquals(
            setOf("DUMBBELL", "BODYWEIGHT", "BAND"),
            EquipmentEnvironmentMap.equipmentsFor(TrainingEnvironment.CASA),
        )
    }

    @Test
    fun `academia nao filtra equipamento`() {
        assertNull(
            EquipmentEnvironmentMap.equipmentsFor(TrainingEnvironment.ACADEMIA),
            "academia vê tudo: sem lista de equipamento",
        )
    }

    @Test
    fun `equipamento caseiro roda nos dois ambientes`() {
        val envs = EquipmentEnvironmentMap.environmentsFor("DUMBBELL")
        assertTrue(TrainingEnvironment.ACADEMIA in envs)
        assertTrue(TrainingEnvironment.CASA in envs)
    }

    @Test
    fun `equipamento de academia so roda na academia`() {
        assertEquals(setOf(TrainingEnvironment.ACADEMIA), EquipmentEnvironmentMap.environmentsFor("BARBELL"))
        assertEquals(setOf(TrainingEnvironment.ACADEMIA), EquipmentEnvironmentMap.environmentsFor("MACHINE"))
    }

    @Test
    fun `equipamento desconhecido cai no fallback conservador (so academia)`() {
        assertEquals(
            setOf(TrainingEnvironment.ACADEMIA),
            EquipmentEnvironmentMap.environmentsFor("EQUIPAMENTO_INEXISTENTE"),
        )
    }
}
