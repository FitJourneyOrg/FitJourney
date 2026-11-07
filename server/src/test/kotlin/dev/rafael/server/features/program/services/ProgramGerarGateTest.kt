package dev.rafael.server.features.program.services

import dev.rafael.contract.error.ErrorCodes
import dev.rafael.contract.profile.Goal
import dev.rafael.contract.profile.HealthScreening
import dev.rafael.contract.profile.Level
import dev.rafael.contract.profile.ProfileDto
import dev.rafael.core.result.AppError
import dev.rafael.core.result.AppResult
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Fatia 12 (`docs/regras/16-onboarding-e-triagem.md`): o gate de saúde (PAR-Q) foi removido
 * de `/programs/generate` -- autoavaliação sem armazenamento nunca bloqueia. Só resta o
 * gate de idade (#24). O teste do "qualquer risco declarado" é o guarda de regressão: prova
 * que o `health` não bloqueia mais, mesmo quando declara risco e não reconhece.
 */
class ProgramGerarGateTest {

    private fun dto(age: Int? = 30, minorSupervised: Boolean = false, health: HealthScreening? = null) = ProfileDto(
        goal = Goal.GAIN_MUSCLE,
        level = Level.INTERMEDIATE,
        daysPerWeek = 3,
        age = age,
        minorSupervised = minorSupervised,
        health = health,
        onboardingCompleted = true,
    )

    @Test
    fun `maior de idade sem risco de saude declarado passa`() {
        val r = ProgramGerarGate.avaliar(dto())
        assertTrue(r is AppResult.Success)
    }

    @Test
    fun `qualquer risco de saude declarado e nao reconhecido NAO bloqueia mais -- guarda de regressao`() {
        val comRisco = HealthScreening(hasCardiacCondition = true, acknowledgedRisk = false)
        val r = ProgramGerarGate.avaliar(dto(health = comRisco))
        assertTrue(r is AppResult.Success)
    }

    @Test
    fun `menor de 18 sem aceite de supervisao continua bloqueando`() {
        val r = ProgramGerarGate.avaliar(dto(age = 16, minorSupervised = false))
        assertTrue(r is AppResult.Failure && r.error is AppError.Forbidden)
        assertTrue((r.error as AppError.Forbidden).code == ErrorCodes.AGE_GATE_REQUIRED)
    }

    @Test
    fun `menor de 18 com aceite de supervisao passa`() {
        val r = ProgramGerarGate.avaliar(dto(age = 16, minorSupervised = true))
        assertTrue(r is AppResult.Success)
    }
}
