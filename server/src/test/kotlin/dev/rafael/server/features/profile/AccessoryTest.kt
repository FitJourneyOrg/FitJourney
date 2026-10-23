package dev.rafael.server.features.profile

import dev.rafael.contract.profile.Accessory
import dev.rafael.contract.profile.TrainingEnvironment
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * `Accessory.paraAmbiente` puro (tier 1 do plano "acessórios no onboarding", §6) -- o
 * `AcessoriosIntegrationTest` já trava o catálogo contra Postgres; aqui é só a regra em si,
 * sem banco: ACADEMIA sempre os seis, CASA exatamente o declarado.
 */
class AccessoryTest {

    @Test
    fun `academia devolve os seis mesmo com lista vazia`() {
        assertEquals(
            Accessory.entries.toSet(),
            Accessory.paraAmbiente(TrainingEnvironment.ACADEMIA, emptyList()),
        )
    }

    @Test
    fun `academia devolve os seis mesmo ignorando o que foi declarado`() {
        assertEquals(
            Accessory.entries.toSet(),
            Accessory.paraAmbiente(TrainingEnvironment.ACADEMIA, listOf(Accessory.BENCH)),
        )
    }

    @Test
    fun `casa devolve exatamente o declarado`() {
        val declarados = listOf(Accessory.BENCH, Accessory.STEP)
        assertEquals(declarados.toSet(), Accessory.paraAmbiente(TrainingEnvironment.CASA, declarados))
    }

    @Test
    fun `casa sem nada declarado nao devolve nenhum`() {
        assertEquals(emptySet(), Accessory.paraAmbiente(TrainingEnvironment.CASA, emptyList()))
    }

    /** Caminho de falha: ambiente nulo (perfil incompleto) não pode virar "academia por engano". */
    @Test
    fun `ambiente nulo nao e tratado como academia`() {
        val declarados = listOf(Accessory.BENCH)
        assertEquals(declarados.toSet(), Accessory.paraAmbiente(null, declarados))
    }
}
