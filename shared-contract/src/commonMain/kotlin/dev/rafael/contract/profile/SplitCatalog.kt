package dev.rafael.contract.profile

/** Uma opção de split na shortlist do onboarding. `recommended` = o ★ (default do motor). */
data class SplitOption(val type: SplitType, val recommended: Boolean)

/**
 * Catálogo curado de splits por nº de dias (ARCH #29) — Kotlin puro, fonte única
 * usada pelo motor (server, ao montar) e pelo quiz (cliente, ao listar).
 *
 * Regra: só opções que preservam frequência decente (~2×/semana, Schoenfeld). O
 * recomendado é o mesmo split que o #26 já derivava. Bro-split/body-part NÃO entra
 * (fere a frequência) — fica como override premium na edição.
 *
 * Tabela ratificada:
 *   2d → Full Body ★
 *   3d → Full Body ★ · Upper/Lower/Full · PPL (1×)
 *   4d → Upper/Lower ★ · Full Body
 *   5d → Upper/Lower + PPL ★
 *   6d → PPL ×2 ★ · Arnold
 */
object SplitCatalog {

    private val byDays: Map<Int, List<SplitOption>> = mapOf(
        2 to listOf(rec(SplitType.FULL_BODY)),
        3 to listOf(rec(SplitType.FULL_BODY), alt(SplitType.UPPER_LOWER_FULL), alt(SplitType.PUSH_PULL_LEGS)),
        4 to listOf(rec(SplitType.UPPER_LOWER), alt(SplitType.FULL_BODY)),
        5 to listOf(rec(SplitType.UL_PPL)),
        6 to listOf(rec(SplitType.PUSH_PULL_LEGS), alt(SplitType.ARNOLD)),
    )

    /** Opções válidas pro nº de dias (recomendado primeiro). daysPerWeek fora de 2..6 é coerção. */
    fun optionsFor(daysPerWeek: Int): List<SplitOption> =
        byDays.getValue(daysPerWeek.coerceIn(2, 6))

    /** Split default (★) pro nº de dias. */
    fun recommendedFor(daysPerWeek: Int): SplitType =
        optionsFor(daysPerWeek).first { it.recommended }.type

    /** true se o split é uma escolha válida pro nº de dias. */
    fun isValid(daysPerWeek: Int, split: SplitType): Boolean =
        optionsFor(daysPerWeek).any { it.type == split }

    /**
     * Resolve o split efetivo: o escolhido se for válido pro nº de dias; senão o
     * recomendado. Blinda o motor contra combinação impossível (ex.: Arnold em 3 dias).
     */
    fun resolve(daysPerWeek: Int, chosen: SplitType?): SplitType =
        chosen?.takeIf { isValid(daysPerWeek, it) } ?: recommendedFor(daysPerWeek)

    private fun rec(t: SplitType) = SplitOption(t, recommended = true)
    private fun alt(t: SplitType) = SplitOption(t, recommended = false)
}
