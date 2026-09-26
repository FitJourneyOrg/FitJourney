package dev.rafael.app.ui

import dev.rafael.contract.group.JoinBlock
import dev.rafael.contract.limites.Limites
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * `JoinBlock.args()` — débito "12 frases cravam constante do servidor" (debitos.md, P2), fechado
 * em 2026-09-23. `enum_bloqueio_lotado` ganhou `%1$d`; este teste garante que quem alimenta o
 * placeholder é [Limites.Group.MAX_MEMBROS], e não um `50` reescrito por engano.
 */
class RotulosTest {

    @Test
    fun `LOTADO carrega o teto de membros`() {
        assertEquals(listOf(Limites.Group.MAX_MEMBROS), JoinBlock.LOTADO.args())
    }

    /** Caminho de falha: os outros quatro motivos não têm placeholder na frase — args tem de ficar vazio. */
    @Test
    fun `os outros motivos de bloqueio nao carregam parametro`() {
        val semParametro = JoinBlock.entries.filter { it != JoinBlock.LOTADO }
        semParametro.forEach { motivo ->
            assertEquals(emptyList(), motivo.args(), "`$motivo` não deveria ter parâmetro")
        }
    }
}
