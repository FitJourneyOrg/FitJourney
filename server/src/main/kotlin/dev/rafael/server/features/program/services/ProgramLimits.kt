package dev.rafael.server.features.program.services

import dev.rafael.contract.error.ErrorCodes
import dev.rafael.core.result.AppError
import dev.rafael.core.result.AppResult
import dev.rafael.core.result.asFailure
import dev.rafael.core.result.asSuccess
import dev.rafael.contract.limites.Limites
import dev.rafael.server.features.program.models.ProgramCounts

/**
 * Política de teto de programas (ARCH #27) — pura, sem HTTP/banco. Antes vivia
 * duplicada dentro das rotas POST /programs/generate e POST /programs; extraída
 * aqui pra ter uma única fonte da regra e ser testável direto.
 *
 * Tetos (decisão do Rafael, 2026-10-08 — antes eram 1 IA + 2 manuais no grátis e 10 no premium):
 *  - grátis: 1 no total, de qualquer tipo (IA ou manual).
 *  - premium: 3 no total (IA + manual).
 *  (Histórico: 2026-10-08 caiu de 2/4 para 1/3, na mesma conversa em que o B9 media o N+1.)
 *
 * O teto só bloqueia CRIAR: quem já tem mais que o teto mantém o que tem (comparação `>=`), e
 * nenhum programa é apagado.
 *
 * Bloqueio grátis → Forbidden com código de **portão de plano** (o cliente abre o paywall). Desde a
 * G.2 cada caso tem código próprio, para poder ter texto próprio, e todos estão em
 * `ErrorCodes.PORTOES_DE_PLANO` — é o conjunto, e não um código único, que o cliente consulta.
 * Bloqueio premium → Forbidden com `LIMITE_DE_PROGRAMAS_PREMIUM`, que **não** está naquele
 * conjunto: é teto duro, não upsell, e oferecer o plano a quem já pagou é pior que não oferecer.
 */
object ProgramLimits {

    // Fonte real: shared-contract (debitos.md "12 frases cravam constante do servidor").
    const val FREE_TOTAL_LIMIT = Limites.Program.FREE_TOTAL_LIMIT
    const val PREMIUM_TOTAL_LIMIT = Limites.Program.PREMIUM_TOTAL_LIMIT

    /** Success(Unit) = pode criar; Failure(Forbidden) = bloqueado (mensagem/code por caso). */
    fun gate(counts: ProgramCounts, isPremium: Boolean): AppResult<Unit> {
        val blocked = counts.total >= if (isPremium) PREMIUM_TOTAL_LIMIT else FREE_TOTAL_LIMIT

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
        return AppError.Forbidden(
            "Limite de programas do plano grátis: $FREE_TOTAL_LIMIT. Assine o premium pra criar mais.",
            ErrorCodes.LIMITE_DE_PROGRAMAS_GRATIS,
        ).asFailure()
    }
}
