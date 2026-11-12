package dev.rafael.server.features.program.services

import dev.rafael.contract.error.ErrorCodes
import dev.rafael.contract.profile.ageGateSatisfied
import dev.rafael.core.result.AppError
import dev.rafael.core.result.AppResult
import dev.rafael.core.result.asSuccess
import dev.rafael.core.result.asFailure
import dev.rafael.contract.profile.ProfileDto

/**
 * Gates pré-condição de `/programs/generate` (pura, extraída de `ProgramRoutes` pra ser
 * testável sem HTTP/banco — mesmo padrão de [ProgramLimits]).
 *
 * **Fatia 12** (`docs/regras/16-onboarding-e-triagem.md`): o gate de saúde (PAR-Q) foi
 * REMOVIDO daqui — a decisão da especificação (3ª rodada de revisão) foi virar autoavaliação
 * sem armazenamento, que nunca bloqueia. Só resta o gate de idade (#24).
 */
object ProgramGerarGate {

    fun avaliar(profile: ProfileDto): AppResult<Unit> =
        if (!ageGateSatisfied(profile.age, profile.minorSupervised)) {
            AppError.Forbidden(
                "Menor de 18: é preciso o reconhecimento de supervisão de um responsável.",
                ErrorCodes.AGE_GATE_REQUIRED,
            ).asFailure()
        } else {
            Unit.asSuccess()
        }
}
