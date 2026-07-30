package dev.rafael.features.profile.data

import dev.rafael.contract.profile.Goal
import dev.rafael.contract.profile.Level
import dev.rafael.contract.profile.MuscleGroup
import dev.rafael.contract.profile.ProfileDto
import dev.rafael.contract.profile.SplitType
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
}
