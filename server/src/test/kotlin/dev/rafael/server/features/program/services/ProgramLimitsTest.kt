package dev.rafael.server.features.program.services

import dev.rafael.contract.error.ErrorCodes
import dev.rafael.core.result.AppError
import dev.rafael.core.result.AppResult
import dev.rafael.server.features.program.models.ProgramCounts
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Testa a política de teto de programas (ARCH #26) — pura. Cobre a regra de
 * monetização que antes estava embutida (e duplicada) nas rotas:
 * grátis 1 IA + 2 manuais (separados); premium 10 no total.
 */
class ProgramLimitsTest {

    private fun gate(ai: Int, manual: Int, premium: Boolean, kind: ProgramLimits.Kind) =
        ProgramLimits.gate(ProgramCounts(ai = ai, manual = manual), premium, kind)

    // ---------- grátis ----------

    @Test
    fun `gratis permite a 1a IA`() {
        assertTrue(gate(ai = 0, manual = 0, premium = false, kind = ProgramLimits.Kind.AI) is AppResult.Success)
    }

    @Test
    fun `gratis bloqueia a 2a IA com code ENTITLEMENT`() {
        val r = gate(ai = 1, manual = 0, premium = false, kind = ProgramLimits.Kind.AI)
        assertIs<AppResult.Failure>(r)
        val err = r.error
        assertIs<AppError.Forbidden>(err)
        assertEquals(ErrorCodes.ENTITLEMENT_REQUIRED, err.code)
    }

    @Test
    fun `gratis permite ate 2 manuais`() {
        assertTrue(gate(ai = 0, manual = 1, premium = false, kind = ProgramLimits.Kind.MANUAL) is AppResult.Success)
    }

    @Test
    fun `gratis bloqueia o 3o manual com code ENTITLEMENT`() {
        val r = gate(ai = 0, manual = 2, premium = false, kind = ProgramLimits.Kind.MANUAL)
        assertIs<AppResult.Failure>(r)
        assertEquals(ErrorCodes.ENTITLEMENT_REQUIRED, (r.error as AppError.Forbidden).code)
    }

    @Test
    fun `gratis conta IA e manual SEPARADAMENTE`() {
        // no teto de IA (1), mas manual livre → pode criar manual
        assertTrue(gate(ai = 1, manual = 0, premium = false, kind = ProgramLimits.Kind.MANUAL) is AppResult.Success)
        // no teto de manual (2), mas IA livre → pode gerar por IA
        assertTrue(gate(ai = 0, manual = 2, premium = false, kind = ProgramLimits.Kind.AI) is AppResult.Success)
    }

    // ---------- premium ----------

    @Test
    fun `premium permite ate 9 (total) livremente`() {
        assertTrue(gate(ai = 5, manual = 4, premium = true, kind = ProgramLimits.Kind.AI) is AppResult.Success)
    }

    @Test
    fun `premium bloqueia no total 10 sem code (teto duro)`() {
        val r = gate(ai = 5, manual = 5, premium = true, kind = ProgramLimits.Kind.AI)
        assertIs<AppResult.Failure>(r)
        val err = r.error
        assertIs<AppError.Forbidden>(err)
        assertNull(err.code, "teto premium não é upsell, então sem ENTITLEMENT")
    }

    @Test
    fun `premium ignora o teto separado do gratis`() {
        // grátis bloquearia (ai >= 1), mas premium olha só o total
        assertTrue(gate(ai = 3, manual = 0, premium = true, kind = ProgramLimits.Kind.AI) is AppResult.Success)
    }
}
