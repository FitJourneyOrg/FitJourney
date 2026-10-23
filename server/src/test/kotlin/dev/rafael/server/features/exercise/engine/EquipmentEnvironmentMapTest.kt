package dev.rafael.server.features.exercise.engine

import dev.rafael.contract.profile.TrainingEnvironment
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Testa o mapa equipamento→ambiente (ARCH #28) — puro.
 * Academia vê tudo (sem filtro); Casa restringe a halter + corporal + elástico + hand grip.
 */
class EquipmentEnvironmentMapTest {

    @Test
    fun `casa restringe a halter corporal elastico e hand grip`() {
        assertEquals(
            setOf("DUMBBELL", "BODYWEIGHT", "BAND", "HAND_GRIP"),
            EquipmentEnvironmentMap.equipmentsFor(TrainingEnvironment.CASA),
        )
    }

    /**
     * O `HAND_GRIP` entrou em 21/10 porque o conjunto discordava da coluna `ambiente` da V65, e
     * um exercício da lista principal pagava: o **Hand Grip (Flexor de Mão)**, curado como
     * `ambiente = CASA`, era recusado pela academia (pelo ambiente) e pela casa (por este
     * conjunto) — não era alcançável por perfil nenhum. Quem prova a ausência do buraco é o
     * `AcessoriosIntegrationTest`, contra o catálogo real; aqui fica só a trava do conjunto.
     */
    @Test
    fun `hand grip roda em casa`() {
        assertTrue(TrainingEnvironment.CASA in EquipmentEnvironmentMap.environmentsFor("HAND_GRIP"))
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
