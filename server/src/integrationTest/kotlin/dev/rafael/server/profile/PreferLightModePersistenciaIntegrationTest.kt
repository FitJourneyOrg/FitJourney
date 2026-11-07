package dev.rafael.server.profile

import dev.rafael.contract.profile.Goal
import dev.rafael.contract.profile.Level
import dev.rafael.contract.profile.ProfileDto
import dev.rafael.contract.profile.TrainingEnvironment
import dev.rafael.core.result.AppResult
import dev.rafael.server.BancoDeTeste
import dev.rafael.server.Semear
import dev.rafael.server.features.profile.db.ProfileRepositoryImpl
import dev.rafael.server.features.profile.models.Profile
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

/**
 * Trava a coluna nova da fatia "modo leve" (V80, resto da Fatia 12) contra Postgres REAL. Mesmo
 * padrão de `MinutosDisponiveisPersistenciaIntegrationTest`.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PreferLightModePersistenciaIntegrationTest {

    private val repo = ProfileRepositoryImpl()

    @BeforeAll
    fun setup() {
        BancoDeTeste.dataSource
        BancoDeTeste.limpar()
    }

    private fun <T> ok(r: AppResult<T>): T = (r as AppResult.Success).value

    private fun perfil(userId: kotlin.uuid.Uuid, preferiu: Boolean?) = Profile(
        userId = userId,
        goal = Goal.GAIN_MUSCLE,
        level = Level.INTERMEDIATE,
        daysPerWeek = 3,
        preferLightMode = preferiu,
        splitPreference = null,
        unavailableDays = emptyList(),
        focusAreas = emptyList(),
        weightKg = null,
        heightCm = null,
        age = null,
        minorSupervised = false,
        environment = TrainingEnvironment.ACADEMIA,
        health = null,
        limitations = emptyList(),
        accessories = null,
        onboardingCompleted = true,
    )

    @Test
    fun `salvar preferLightMode true e reler devolve o mesmo valor`(): Unit = runBlocking {
        val uid = Semear.usuario()

        ok(repo.upsert(perfil(uid, true)))

        val lido = ok(repo.findByUserId(uid))!!
        assertEquals(true, lido.preferLightMode)
    }

    /** Caminho de falha: ninguém respondeu a pergunta ainda -- fica `null`. */
    @Test
    fun `perfil sem a pergunta nova grava e le null, sem quebrar`(): Unit = runBlocking {
        val uid = Semear.usuario()

        ok(repo.upsert(perfil(uid, null)))

        val lido = ok(repo.findByUserId(uid))!!
        assertNull(lido.preferLightMode)
    }

    /** Cliente antigo não manda a chave no JSON -- decodifica cru, default cobre a ausência. */
    @Test
    fun `JSON sem a chave preferLightMode nao quebra a desserializacao (cliente antigo)`() {
        val semAChave = """
            {"goal":"GAIN_MUSCLE","level":"INTERMEDIATE","daysPerWeek":3}
        """.trimIndent()

        val dto = Json.decodeFromString<ProfileDto>(semAChave)

        assertNull(dto.preferLightMode)
    }
}
