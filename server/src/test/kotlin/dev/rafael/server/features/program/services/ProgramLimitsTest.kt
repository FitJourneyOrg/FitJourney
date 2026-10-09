package dev.rafael.server.features.program.services

import dev.rafael.contract.error.ErrorCodes
import dev.rafael.core.result.AppError
import dev.rafael.core.result.AppResult
import dev.rafael.server.features.program.models.ProgramCounts
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Testa a política de teto de programas (ARCH #27) — pura. Regra de monetização (decisão do
 * Rafael, 2026-10-08): grátis 1 no total, de qualquer tipo; premium 3 no total. O teto só bloqueia
 * criar, nunca apaga.
 */
class ProgramLimitsTest {

    private fun gate(ai: Int, manual: Int, premium: Boolean) =
        ProgramLimits.gate(ProgramCounts(ai = ai, manual = manual), premium)

    // ---------- grátis ----------

    @Test
    fun `gratis permite o 1o programa`() {
        assertTrue(gate(ai = 0, manual = 0, premium = false) is AppResult.Success)
    }

    @Test
    /**
     * G.2: o código é próprio para a frase poder ser própria. **O que importa não é o valor, é estar
     * em `PORTOES_DE_PLANO`** — é o conjunto que o cliente consulta para abrir o paywall.
     */
    fun `gratis bloqueia o 2o programa com portao de plano, qualquer que seja o tipo do primeiro`() {
        listOf(1 to 0, 0 to 1, 1 to 1).forEach { (ai, manual) ->
            val r = gate(ai = ai, manual = manual, premium = false)
            assertIs<AppResult.Failure>(r, "ai=$ai manual=$manual")
            val err = r.error
            assertIs<AppError.Forbidden>(err)
            assertEquals(ErrorCodes.LIMITE_DE_PROGRAMAS_GRATIS, err.code)
            assertTrue(err.code in ErrorCodes.PORTOES_DE_PLANO, "precisa abrir o paywall")
        }
    }

    @Test
    fun `gratis que ja tem mais que o teto continua bloqueado, sem perder nada`() {
        // Dado legado de quando o teto era 1 IA + 2 manuais: 3 programas (hoje o teto é 1). O gate só diz "não cria".
        val r = gate(ai = 1, manual = 2, premium = false)
        assertIs<AppResult.Failure>(r)
        assertEquals(ErrorCodes.LIMITE_DE_PROGRAMAS_GRATIS, (r.error as AppError.Forbidden).code)
    }

    // ---------- premium ----------

    @Test
    fun `premium permite ate 2 livremente`() {
        assertTrue(gate(ai = 1, manual = 1, premium = true) is AppResult.Success)
    }

    @Test
    /**
     * ⭐ O teto do premium tem código, e ele **NÃO** é portão de plano: **oferecer o plano a quem
     * acabou de pagar é pior que não oferecer nada.**
     */
    fun `teto do premium NAO e portao de plano`() {
        val r = gate(ai = 2, manual = 1, premium = true)
        assertIs<AppResult.Failure>(r)
        val err = r.error
        assertIs<AppError.Forbidden>(err)
        assertEquals(ErrorCodes.LIMITE_DE_PROGRAMAS_PREMIUM, err.code)
        assertTrue(
            err.code !in ErrorCodes.PORTOES_DE_PLANO,
            "teto de quem já é premium não pode abrir o paywall",
        )
    }

    @Test
    fun `premium com 10 programas do teto antigo continua bloqueado, sem apagar`() {
        val r = gate(ai = 5, manual = 5, premium = true)
        assertIs<AppResult.Failure>(r)
        assertEquals(ErrorCodes.LIMITE_DE_PROGRAMAS_PREMIUM, (r.error as AppError.Forbidden).code)
    }

    @Test
    fun `premium ignora o teto do gratis`() {
        // grátis bloquearia (total >= 1), mas premium só bloqueia a partir de 3
        assertTrue(gate(ai = 2, manual = 0, premium = true) is AppResult.Success)
    }
}
