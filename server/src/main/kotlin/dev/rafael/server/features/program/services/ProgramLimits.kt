package dev.rafael.server.features.program.services

import dev.rafael.contract.error.ErrorCodes
import dev.rafael.core.result.AppError
import dev.rafael.core.result.AppResult
import dev.rafael.core.result.asFailure
import dev.rafael.core.result.asSuccess
import dev.rafael.server.features.program.models.ProgramCounts

/**
 * Política de teto de programas (ARCH #26) — pura, sem HTTP/banco. Antes vivia
 * duplicada dentro das rotas POST /programs/generate e POST /programs; extraída
 * aqui pra ter uma única fonte da regra e ser testável direto.
 *
 * Tetos:
 *  - grátis: 1 gerado por IA + 2 manuais (contados SEPARADAMENTE).
 *  - premium: 10 no total (IA + manual).
 *
 * Bloqueio grátis → Forbidden com code ENTITLEMENT_REQUIRED (cliente abre paywall).
 * Bloqueio premium → Forbidden sem code (é teto duro, não upsell).
 */
object ProgramLimits {

    const val FREE_AI_LIMIT = 1
    const val FREE_MANUAL_LIMIT = 2
    const val PREMIUM_TOTAL_LIMIT = 10

    enum class Kind { AI, MANUAL }

    /** Success(Unit) = pode criar; Failure(Forbidden) = bloqueado (mensagem/code por caso). */
    fun gate(counts: ProgramCounts, isPremium: Boolean, kind: Kind): AppResult<Unit> {
        val blocked =
            if (isPremium) counts.total >= PREMIUM_TOTAL_LIMIT
            else when (kind) {
                Kind.AI -> counts.ai >= FREE_AI_LIMIT
                Kind.MANUAL -> counts.manual >= FREE_MANUAL_LIMIT
            }

        if (!blocked) return Unit.asSuccess()

        if (isPremium) {
            return AppError.Forbidden(
                "Você atingiu o limite máximo de $PREMIUM_TOTAL_LIMIT programas.",
            ).asFailure()
        }
        return when (kind) {
            Kind.AI -> AppError.Forbidden(
                "Gerar treino por IA é limitado a $FREE_AI_LIMIT no plano grátis. Assine o premium pra gerar mais.",
                ErrorCodes.ENTITLEMENT_REQUIRED,
            ).asFailure()
            Kind.MANUAL -> AppError.Forbidden(
                "Criar programas é limitado a $FREE_MANUAL_LIMIT no plano grátis. Assine o premium pra criar mais.",
                ErrorCodes.ENTITLEMENT_REQUIRED,
            ).asFailure()
        }
    }
}
