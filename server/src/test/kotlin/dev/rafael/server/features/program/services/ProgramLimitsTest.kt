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
 * Testa a política de teto de programas (ARCH #27) — pura. Cobre a regra de
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
    /**
     * G.2: o código deixou de ser `ENTITLEMENT_REQUIRED` e virou próprio, para a frase poder ser
     * própria. **O que importa não é o valor, é estar em `PORTOES_DE_PLANO`** — é o conjunto que o
     * cliente consulta para abrir o paywall, e afirmar o valor exato aqui não provaria isso.
     */
    fun `gratis bloqueia a 2a IA com portao de plano`() {
        val r = gate(ai = 1, manual = 0, premium = false, kind = ProgramLimits.Kind.AI)
        assertIs<AppResult.Failure>(r)
        val err = r.error
        assertIs<AppError.Forbidden>(err)
        assertEquals(ErrorCodes.LIMITE_DE_IA_GRATIS, err.code)
        assertTrue(err.code in ErrorCodes.PORTOES_DE_PLANO, "precisa abrir o paywall")
    }

    @Test
    fun `gratis permite ate 2 manuais`() {
        assertTrue(gate(ai = 0, manual = 1, premium = false, kind = ProgramLimits.Kind.MANUAL) is AppResult.Success)
    }

    @Test
    fun `gratis bloqueia o 3o manual com portao de plano`() {
        val r = gate(ai = 0, manual = 2, premium = false, kind = ProgramLimits.Kind.MANUAL)
        assertIs<AppResult.Failure>(r)
        val code = (r.error as AppError.Forbidden).code
        assertEquals(ErrorCodes.LIMITE_DE_MANUAIS_GRATIS, code)
        assertTrue(code in ErrorCodes.PORTOES_DE_PLANO, "precisa abrir o paywall")
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
    /**
     * ⭐ O teto do premium tem código, e ele **NÃO** é portão de plano.
     *
     * Antes da G.2 o `code` era nulo, e o nulo carregava sozinho a decisão de não abrir o paywall.
     * Isso funcionava e não dizia por quê: nulo é ausência, e ausência não explica nada.
     *
     * Agora o código existe (a frase precisa dele) e a decisão está no CONJUNTO. É a asserção de
     * baixo que guarda a regra: **oferecer o plano a quem acabou de pagar é pior que não oferecer
     * nada.**
     */
    fun `teto do premium NAO e portao de plano`() {
        val r = gate(ai = 5, manual = 5, premium = true, kind = ProgramLimits.Kind.AI)
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
    fun `premium ignora o teto separado do gratis`() {
        // grátis bloquearia (ai >= 1), mas premium olha só o total
        assertTrue(gate(ai = 3, manual = 0, premium = true, kind = ProgramLimits.Kind.AI) is AppResult.Success)
    }
}
