package dev.rafael.server.features.program.services

import dev.rafael.contract.error.ErrorCodes
import dev.rafael.core.result.AppError
import dev.rafael.core.result.AppResult
import dev.rafael.core.result.asFailure
import dev.rafael.core.result.asSuccess
import dev.rafael.server.features.program.models.ProgramCounts

/**
 * Política de teto de programas (ARCH #27) — pura, sem HTTP/banco. Antes vivia
 * duplicada dentro das rotas POST /programs/generate e POST /programs; extraída
 * aqui pra ter uma única fonte da regra e ser testável direto.
 *
 * Tetos:
 *  - grátis: 1 gerado por IA + 2 manuais (contados SEPARADAMENTE).
 *  - premium: 10 no total (IA + manual).
 *
 * Bloqueio grátis → Forbidden com código de **portão de plano** (o cliente abre o paywall). Desde a
 * G.2 cada caso tem código próprio, para poder ter texto próprio, e todos estão em
 * `ErrorCodes.PORTOES_DE_PLANO` — é o conjunto, e não um código único, que o cliente consulta.
 * Bloqueio premium → Forbidden com `LIMITE_DE_PROGRAMAS_PREMIUM`, que **não** está naquele
 * conjunto: é teto duro, não upsell, e oferecer o plano a quem já pagou é pior que não oferecer.
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
            // ⚠️ NÃO usa `ENTITLEMENT_REQUIRED`, e a diferença importa: aqui a pessoa JÁ é premium,
            // e mandá-la ao paywall seria oferecer o que ela acabou de pagar. É teto de uso, não
            // portão de plano.
            return AppError.Forbidden(
                "Você atingiu o limite máximo de $PREMIUM_TOTAL_LIMIT programas.",
                ErrorCodes.LIMITE_DE_PROGRAMAS_PREMIUM,
            ).asFailure()
        }
        return when (kind) {
            Kind.AI -> AppError.Forbidden(
                "Gerar treino por IA é limitado a $FREE_AI_LIMIT no plano grátis. Assine o premium pra gerar mais.",
                ErrorCodes.LIMITE_DE_IA_GRATIS,
            ).asFailure()
            Kind.MANUAL -> AppError.Forbidden(
                "Criar programas é limitado a $FREE_MANUAL_LIMIT no plano grátis. Assine o premium pra criar mais.",
                ErrorCodes.LIMITE_DE_MANUAIS_GRATIS,
            ).asFailure()
        }
    }
}
