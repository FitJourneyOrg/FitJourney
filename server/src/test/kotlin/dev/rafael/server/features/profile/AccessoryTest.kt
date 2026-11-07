package dev.rafael.server.features.profile

import dev.rafael.contract.profile.Accessory
import dev.rafael.contract.profile.TrainingEnvironment
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * `Accessory.paraAmbiente` puro (tier 1 do plano "acessórios no onboarding", §6; atualizado na
 * fatia "não tenho este equipamento", resto da Fatia 12) -- o `AcessoriosIntegrationTest` já
 * trava o catálogo contra Postgres; aqui é só a regra em si, sem banco. `null` = nunca
 * perguntado (ACADEMIA assume os seis, CASA assume nenhum); lista explícita (mesmo vazia) é
 * respeitada nos dois ambientes.
 */
class AccessoryTest {

    @Test
    fun `academia sem ninguem ter perguntado (null) devolve os seis`() {
        assertEquals(
            Accessory.entries.toSet(),
            Accessory.paraAmbiente(TrainingEnvironment.ACADEMIA, null),
        )
    }

    /**
     * Fatia "não tenho este equipamento": na academia, uma lista EXPLÍCITA agora é respeitada --
     * mesmo vazia (botão "não tenho nada disso"). Antes desta fatia, academia sempre ignorava o
     * declarado; isto é o caminho de falha do teste anterior, agora o comportamento esperado.
     */
    @Test
    fun `academia com lista explicita (mesmo vazia) respeita o declarado`() {
        assertEquals(
            emptySet(),
            Accessory.paraAmbiente(TrainingEnvironment.ACADEMIA, emptyList()),
        )
        assertEquals(
            setOf(Accessory.BENCH),
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
