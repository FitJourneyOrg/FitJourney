package dev.rafael.features.program.data

import dev.rafael.contract.error.ErrorCodes
import dev.rafael.contract.limites.Limites
import dev.rafael.core.result.AppError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TetoDeProgramasTest {

    private fun codigo(erro: AppError?): String? = (erro as? AppError.Forbidden)?.code

    @Test
    fun `gratis com um programa ainda pode criar`() {
        assertNull(TetoDeProgramas.recusa(total = Limites.Program.FREE_TOTAL_LIMIT - 1, premium = false))
    }

    @Test
    fun `gratis no teto e recusado com portao de plano`() {
        val erro = TetoDeProgramas.recusa(total = Limites.Program.FREE_TOTAL_LIMIT, premium = false)
        assertIs<AppError.Forbidden>(erro)
        assertEquals(ErrorCodes.LIMITE_DE_PROGRAMAS_GRATIS, erro.code)
        assertTrue(erro.code in ErrorCodes.PORTOES_DE_PLANO, "precisa poder abrir o paywall")
    }

    @Test
    fun `gratis acima do teto, dado legado, continua recusado`() {
        assertEquals(
            ErrorCodes.LIMITE_DE_PROGRAMAS_GRATIS,
            codigo(TetoDeProgramas.recusa(total = Limites.Program.FREE_TOTAL_LIMIT + 1, premium = false)),
        )
    }

    @Test
    fun `premium abaixo do teto pode criar`() {
        assertNull(TetoDeProgramas.recusa(total = Limites.Program.PREMIUM_TOTAL_LIMIT - 1, premium = true))
    }

    @Test
    fun `premium no teto e recusado SEM abrir o paywall`() {
        val erro = TetoDeProgramas.recusa(total = Limites.Program.PREMIUM_TOTAL_LIMIT, premium = true)
        assertIs<AppError.Forbidden>(erro)
        assertEquals(ErrorCodes.LIMITE_DE_PROGRAMAS_PREMIUM, erro.code)
        assertTrue(erro.code !in ErrorCodes.PORTOES_DE_PLANO, "quem já é premium não vê o plano de novo")
    }

    @Test
    fun `plano desconhecido nunca bloqueia, quem decide e o servidor`() {
        assertNull(TetoDeProgramas.recusa(total = 99, premium = null))
    }

    @Test
    fun `premium com 2 passa onde o gratis com 2 nao passaria`() {
        assertNull(TetoDeProgramas.recusa(total = 2, premium = true))
        assertEquals(ErrorCodes.LIMITE_DE_PROGRAMAS_GRATIS, codigo(TetoDeProgramas.recusa(total = 2, premium = false)))
    }
}
