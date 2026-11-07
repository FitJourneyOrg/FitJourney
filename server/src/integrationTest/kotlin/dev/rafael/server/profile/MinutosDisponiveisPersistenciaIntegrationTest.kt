package dev.rafael.server.profile

import dev.rafael.contract.profile.Goal
import dev.rafael.contract.profile.Level
import dev.rafael.contract.profile.MinutesAvailable
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
 * Trava a coluna nova da fatia "minutos disponíveis" (V79, resto da Fatia 12) contra Postgres
 * REAL. Mesmo padrão de `NivelDerivadoPersistenciaIntegrationTest`.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MinutosDisponiveisPersistenciaIntegrationTest {

    private val repo = ProfileRepositoryImpl()

    @BeforeAll
    fun setup() {
        BancoDeTeste.dataSource
        BancoDeTeste.limpar()
    }

    private fun <T> ok(r: AppResult<T>): T = (r as AppResult.Success).value

    private fun perfil(userId: kotlin.uuid.Uuid, minutos: MinutesAvailable?) = Profile(
        userId = userId,
        goal = Goal.GAIN_MUSCLE,
        level = Level.INTERMEDIATE,
        daysPerWeek = 3,
        minutesAvailable = minutos,
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
    fun `salvar minutesAvailable e reler devolve o mesmo valor`(): Unit = runBlocking {
        val uid = Semear.usuario()

        ok(repo.upsert(perfil(uid, MinutesAvailable.SIXTY)))

        val lido = ok(repo.findByUserId(uid))!!
        assertEquals(MinutesAvailable.SIXTY, lido.minutesAvailable)
    }

    /** Caminho de falha: ninguém respondeu a pergunta ainda -- fica `null`. */
    @Test
    fun `perfil sem a pergunta nova grava e le null, sem quebrar`(): Unit = runBlocking {
        val uid = Semear.usuario()

        ok(repo.upsert(perfil(uid, null)))

        val lido = ok(repo.findByUserId(uid))!!
        assertNull(lido.minutesAvailable)
    }

    /** Cliente antigo não manda a chave no JSON -- decodifica cru, default cobre a ausência. */
    @Test
    fun `JSON sem a chave minutesAvailable nao quebra a desserializacao (cliente antigo)`() {
        val semAChave = """
            {"goal":"GAIN_MUSCLE","level":"INTERMEDIATE","daysPerWeek":3}
        """.trimIndent()

        val dto = Json.decodeFromString<ProfileDto>(semAChave)

        assertNull(dto.minutesAvailable)
    }
}
