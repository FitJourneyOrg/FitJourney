package dev.rafael.server.profile

import dev.rafael.contract.profile.Goal
import dev.rafael.contract.profile.Level
import dev.rafael.contract.profile.ProfileDto
import dev.rafael.contract.profile.TimeOffTraining
import dev.rafael.contract.profile.TrainingConsistency
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
 * Trava as duas colunas novas da fatia "nível derivado" (V78,
 * `docs/trabalho/nivel-derivado-e-fim-do-gate-de-saude.md`) contra Postgres REAL.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NivelDerivadoPersistenciaIntegrationTest {

    private val repo = ProfileRepositoryImpl()

    @BeforeAll
    fun setup() {
        BancoDeTeste.dataSource
        BancoDeTeste.limpar()
    }

    private fun <T> ok(r: AppResult<T>): T = (r as AppResult.Success).value

    private fun perfil(
        userId: kotlin.uuid.Uuid,
        consistencia: TrainingConsistency?,
        tempoParado: TimeOffTraining?,
    ) = Profile(
        userId = userId,
        goal = Goal.GAIN_MUSCLE,
        level = Level.INTERMEDIATE,
        trainingConsistency = consistencia,
        timeOffTraining = tempoParado,
        daysPerWeek = 3,
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
    fun `salvar os dois campos novos e reler devolve os mesmos valores`(): Unit = runBlocking {
        val uid = Semear.usuario()

        ok(repo.upsert(perfil(uid, TrainingConsistency.SIX_MONTHS_TO_2_YEARS, TimeOffTraining.UP_TO_1_MONTH)))

        val lido = ok(repo.findByUserId(uid))!!
        assertEquals(TrainingConsistency.SIX_MONTHS_TO_2_YEARS, lido.trainingConsistency)
        assertEquals(TimeOffTraining.UP_TO_1_MONTH, lido.timeOffTraining)
    }

    /** Caminho de falha: perfil legado (pré-fatia-12) nunca respondeu as duas -- ficam `null`. */
    @Test
    fun `perfil legado sem as perguntas novas grava e le null, sem quebrar`(): Unit = runBlocking {
        val uid = Semear.usuario()

        ok(repo.upsert(perfil(uid, null, null)))

        val lido = ok(repo.findByUserId(uid))!!
        assertNull(lido.trainingConsistency)
        assertNull(lido.timeOffTraining)
    }

    /**
     * Mesmo caminho de falha do par accessories NULL/[] (ver `AcessoriosPersistenciaIntegrationTest`):
     * um cliente de verdade, sem a feature, nunca manda as duas chaves no JSON -- decodifica o
     * JSON cru e prova que o default (`null`) cobre a ausência sem lançar exceção.
     */
    @Test
    fun `JSON sem as duas chaves novas nao quebra a desserializacao (cliente antigo)`() {
        val semAsChaves = """
            {"goal":"GAIN_MUSCLE","level":"INTERMEDIATE","daysPerWeek":3}
        """.trimIndent()

        val dto = Json.decodeFromString<ProfileDto>(semAsChaves)

        assertNull(dto.trainingConsistency)
        assertNull(dto.timeOffTraining)
    }
}
