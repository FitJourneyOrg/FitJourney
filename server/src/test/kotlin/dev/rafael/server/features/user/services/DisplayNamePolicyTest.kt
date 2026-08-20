package dev.rafael.server.features.user.services

import dev.rafael.core.result.AppError
import dev.rafael.core.result.AppResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/**
 * A política do `display_name` (ARCH #33, fatia A.0).
 *
 * Por que vale teste puro: esta é a regra que decide o nome que 49 desconhecidos vão ver no
 * ranking, e ela roda no caminho mais crítico que existe — a criação do usuário, no primeiro
 * `GET /me`. Um `inicial()` que devolvesse string vazia estouraria o CHECK do banco no login,
 * e o usuário veria "erro interno" sem nunca saber por quê.
 */
class DisplayNamePolicyTest {

    private val id = Uuid.parse("3f1a2b4c-5d6e-7081-9234-56789abcdef0")

    private fun ok(r: AppResult<String>): String = (r as AppResult.Success).value
    private fun erro(r: AppResult<String>): AppError.Validation =
        (r as AppResult.Failure).error as AppError.Validation

    // --- inicial() ---

    @Test
    fun `usa a parte local do e-mail`() {
        assertEquals("rafel0017", DisplayNamePolicy.inicial("rafel0017@gmail.com", id))
    }

    @Test
    fun `e-mail nulo cai no fallback pelo id`() {
        // Login por provedor pode não devolver e-mail, e a coluna users.email é nullable desde
        // a V1. Sem fallback, o insert violaria o NOT NULL do display_name.
        assertEquals("Atleta-3f1a2b", DisplayNamePolicy.inicial(null, id))
    }

    @Test
    fun `parte local curta demais cai no fallback`() {
        // "a@x.com" daria um nome de 1 caractere, que o CHECK do banco recusa.
        assertEquals("Atleta-3f1a2b", DisplayNamePolicy.inicial("a@x.com", id))
    }

    @Test
    fun `parte local longa e truncada no limite da coluna`() {
        val longo = "a".repeat(50)
        val nome = DisplayNamePolicy.inicial("$longo@x.com", id)

        assertEquals(DisplayNamePolicy.MAX, nome.length, "não pode estourar o VARCHAR(30)")
    }

    @Test
    fun `o inicial SEMPRE satisfaz o normalizar`() {
        // Invariante que amarra as duas metades: o nome que nasce nunca pode ser um nome que a
        // validação recusaria. Se um dia divergirem, o usuário fica preso — a tela de perfil
        // acusaria erro num nome que ele nunca escolheu.
        listOf(null, "", "a@x.com", "ab@x.com", "a".repeat(50) + "@x.com", "  @x.com")
            .forEach { email ->
                val nome = DisplayNamePolicy.inicial(email, id)
                assertTrue(
                    DisplayNamePolicy.normalizar(nome) is AppResult.Success,
                    "inicial($email) = '$nome' foi recusado pelo normalizar",
                )
            }
    }

    // --- normalizar() ---

    @Test
    fun `apara as pontas`() {
        assertEquals("Rafael", ok(DisplayNamePolicy.normalizar("  Rafael  ")))
    }

    @Test
    fun `colapsa espacos internos antes de medir`() {
        // "  Rafael   Souza " tem 17 caracteres crus e 13 reais. Medir o cru recusaria um nome
        // legítimo por causa de espaço que a gente mesmo ia descartar.
        assertEquals("Rafael Souza", ok(DisplayNamePolicy.normalizar("  Rafael   Souza ")))
    }

    @Test
    fun `quebra de linha colada de outro app vira espaco`() {
        // Sem isto, o nome quebraria o layout da linha do ranking.
        assertEquals("Rafael Souza", ok(DisplayNamePolicy.normalizar("Rafael\nSouza")))
    }

    @Test
    fun `recusa curto demais`() {
        val e = erro(DisplayNamePolicy.normalizar("R"))
        assertTrue("displayName" in e.fieldErrors, "o erro tem de apontar o campo, p/ a UI marcar")
    }

    @Test
    fun `so espaco e curto demais, nao valido`() {
        assertTrue(DisplayNamePolicy.normalizar("     ") is AppResult.Failure)
    }

    @Test
    fun `recusa longo demais`() {
        assertTrue(DisplayNamePolicy.normalizar("a".repeat(DisplayNamePolicy.MAX + 1)) is AppResult.Failure)
    }

    @Test
    fun `aceita exatamente nos limites`() {
        assertEquals("ab", ok(DisplayNamePolicy.normalizar("ab")))
        val max = "a".repeat(DisplayNamePolicy.MAX)
        assertEquals(max, ok(DisplayNamePolicy.normalizar(max)))
    }

    @Test
    fun `nome repetido e permitido - nao ha unicidade`() {
        // [REGRA] #33: nome de pessoa não é identificador. Duas pessoas podem se chamar Rafael;
        // quem identifica é o id. Este teste existe para que "vamos exigir único" seja uma
        // decisão consciente, e não algo que alguém acrescente sem perceber a consequência.
        assertEquals("Rafael", ok(DisplayNamePolicy.normalizar("Rafael")))
        assertEquals("Rafael", ok(DisplayNamePolicy.normalizar("Rafael")))
    }
}
