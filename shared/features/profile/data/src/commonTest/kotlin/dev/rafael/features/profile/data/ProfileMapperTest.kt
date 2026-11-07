package dev.rafael.features.profile.data

import dev.rafael.contract.profile.Accessory
import dev.rafael.contract.profile.Goal
import dev.rafael.contract.profile.Level
import dev.rafael.contract.profile.MinutesAvailable
import dev.rafael.contract.profile.MuscleGroup
import dev.rafael.contract.profile.ProfileDto
import dev.rafael.contract.profile.SplitType
import dev.rafael.contract.profile.TimeOffTraining
import dev.rafael.contract.profile.TrainingConsistency
import dev.rafael.contract.profile.TrainingEnvironment
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Testa o mapper de Profile (cliente) — puro. Foco no ARCH #29: splitPreference
 * sobrevive ao DTO↔domínio (senão a escolha do quiz nunca chega ao server).
 */
class ProfileMapperTest {

    private fun dto() = ProfileDto(
        goal = Goal.GAIN_MUSCLE,
        level = Level.INTERMEDIATE,
        daysPerWeek = 6,
        splitPreference = SplitType.ARNOLD,
        focusAreas = listOf(MuscleGroup.CHEST),
        weightKg = 80.0,
        heightCm = 180.0,
        environment = TrainingEnvironment.ACADEMIA,
        unavailableDays = listOf(6, 7),
        onboardingCompleted = true,
    )

    @Test
    fun `round-trip preserva unavailableDays (Estagio 2)`() {
        val rt = dto().toDomain().toDto()
        assertEquals(listOf(6, 7), rt.unavailableDays)
    }

    @Test
    fun `toDomain preserva splitPreference`() {
        val d = dto().toDomain()
        assertEquals(SplitType.ARNOLD, d.splitPreference)
        assertEquals(6, d.daysPerWeek)
        assertEquals(TrainingEnvironment.ACADEMIA, d.environment)
    }

    @Test
    fun `round-trip DTO para dominio para DTO mantem splitPreference`() {
        val rt = dto().toDomain().toDto()
        assertEquals(SplitType.ARNOLD, rt.splitPreference)
        assertEquals(6, rt.daysPerWeek)
    }

    @Test
    fun `splitPreference nula (recomendado) sobrevive`() {
        assertNull(dto().copy(splitPreference = null).toDomain().splitPreference)
    }

    // ---- V66: accessories, a distinção null (nunca perguntado) x [] (perguntado, nada) ----

    @Test
    fun `accessories declarados sobrevivem ao round-trip`() {
        val declarados = listOf(Accessory.BENCH, Accessory.ANCHOR_POINT)
        val rt = dto().copy(accessories = declarados).toDomain().toDto()
        assertEquals(declarados, rt.accessories)
    }

    @Test
    fun `accessories vazia nao vira nula no round-trip`() {
        val rt = dto().copy(accessories = emptyList()).toDomain().toDto()
        assertEquals(emptyList<Accessory>(), rt.accessories)
    }

    /** Caminho de falha: o default é nulo -- quem não passa nada não pode acabar com `[]`. */
    @Test
    fun `accessories nao informado fica nulo, nao vazio`() {
        assertNull(dto().toDomain().accessories)
    }

    // ---- fatia 12 (resto, cliente): trainingConsistency e timeOffTraining sobrevivem ao DTO<->dominio ----

    @Test
    fun `trainingConsistency e timeOffTraining sobrevivem ao round-trip`() {
        val d = dto().copy(
            trainingConsistency = TrainingConsistency.SIX_MONTHS_TO_2_YEARS,
            timeOffTraining = TimeOffTraining.UP_TO_1_MONTH,
        )
        val rt = d.toDomain().toDto()
        assertEquals(TrainingConsistency.SIX_MONTHS_TO_2_YEARS, rt.trainingConsistency)
        assertEquals(TimeOffTraining.UP_TO_1_MONTH, rt.timeOffTraining)
    }

    /** Caminho de falha: quem ainda não respondeu as perguntas novas fica nulo nos dois campos. */
    @Test
    fun `trainingConsistency e timeOffTraining nao informados ficam nulos`() {
        val d = dto().toDomain()
        assertNull(d.trainingConsistency)
        assertNull(d.timeOffTraining)
    }

    // ---- fatia 12 (resto, cliente): minutesAvailable sobrevive ao DTO<->dominio ----

    @Test
    fun `minutesAvailable sobrevive ao round-trip`() {
        val rt = dto().copy(minutesAvailable = MinutesAvailable.SEVENTY_FIVE).toDomain().toDto()
        assertEquals(MinutesAvailable.SEVENTY_FIVE, rt.minutesAvailable)
    }

    @Test
    fun `minutesAvailable nao informado fica nulo`() {
        assertNull(dto().toDomain().minutesAvailable)
    }
}
