package dev.rafael.core.database.outbox

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A compactação decide o que sai do aparelho — é a regra mais consequente do outbox.
 * Sendo lógica pura, dá pra testar cada caso sem banco, sem rede e sem emulador.
 */
class CompactadorDeOutboxTest {

    private var proximo = 0L
    private fun op(tipo: TipoOperacao, alvo: String, payload: String = "{}") =
        Operacao(seq = proximo++, tipo = tipo, alvoId = alvo, payload = payload)

    @Test
    fun `criar mais edicoes vira uma criacao com o estado final`() {
        val fila = listOf(
            op(TipoOperacao.CRIAR_TREINO, "w1", """{"name":"Costas B"}"""),
            op(TipoOperacao.EDITAR_TREINO, "w1", """{"name":"Costas B","ex":4}"""),
            op(TipoOperacao.EDITAR_TREINO, "w1", """{"name":"Costas + Biceps","ex":4}"""),
        )

        val r = CompactadorDeOutbox.compactar(fila)

        assertEquals(1, r.size, "3 comandos sobre o mesmo alvo deveriam virar 1 requisição")
        // O TIPO tem que continuar sendo criação (decide POST), mas com o payload da última.
        assertEquals(TipoOperacao.CRIAR_TREINO, r[0].tipo)
        assertEquals("""{"name":"Costas + Biceps","ex":4}""", r[0].payload)
    }

    @Test
    fun `criar e excluir offline nao envia nada`() {
        val fila = listOf(
            op(TipoOperacao.CRIAR_TREINO, "w1"),
            op(TipoOperacao.EXCLUIR_TREINO, "w1"),
        )

        // O servidor nunca soube que este treino existiu. Enviar POST+DELETE seria pior que
        // não enviar: se a rede cair entre os dois, sobra um recurso fantasma.
        assertTrue(CompactadorDeOutbox.compactar(fila).isEmpty())
    }

    @Test
    fun `edicoes de recurso que ja existe viram a ultima`() {
        val fila = listOf(
            op(TipoOperacao.EDITAR_TREINO, "w1", """{"v":1}"""),
            op(TipoOperacao.EDITAR_TREINO, "w1", """{"v":2}"""),
            op(TipoOperacao.EDITAR_TREINO, "w1", """{"v":3}"""),
        )

        val r = CompactadorDeOutbox.compactar(fila)

        assertEquals(1, r.size)
        assertEquals("""{"v":3}""", r[0].payload)
    }

    @Test
    fun `editar e depois excluir vira exclusao`() {
        val fila = listOf(
            op(TipoOperacao.EDITAR_TREINO, "w1"),
            op(TipoOperacao.EXCLUIR_TREINO, "w1"),
        )

        val r = CompactadorDeOutbox.compactar(fila)

        // Aqui o recurso EXISTE no servidor — diferente do caso criar+excluir, o DELETE precisa ir.
        assertEquals(1, r.size)
        assertEquals(TipoOperacao.EXCLUIR_TREINO, r[0].tipo)
    }

    @Test
    fun `ordem entre alvos diferentes e preservada`() {
        // O caso que a agenda impõe: criar o treino precisa subir ANTES de agendá-lo, senão o
        // servidor recusa ("a agenda precisa cobrir exatamente os treinos do programa").
        val fila = listOf(
            op(TipoOperacao.CRIAR_TREINO, "w1"),
            op(TipoOperacao.DEFINIR_AGENDA, "p1"),
            op(TipoOperacao.EDITAR_TREINO, "w1"),
        )

        val r = CompactadorDeOutbox.compactar(fila)

        assertEquals(2, r.size)
        assertEquals("w1", r[0].alvoId, "o treino tem que subir antes da agenda que o referencia")
        assertEquals("p1", r[1].alvoId)
    }

    @Test
    fun `alvos independentes sobrevivem todos`() {
        val fila = listOf(
            op(TipoOperacao.CRIAR_TREINO, "w1"),
            op(TipoOperacao.CRIAR_TREINO, "w2"),
            op(TipoOperacao.EDITAR_TREINO, "w1"),
            op(TipoOperacao.CRIAR_PROGRAMA, "p1"),
        )

        val r = CompactadorDeOutbox.compactar(fila)

        assertEquals(3, r.size)
        assertEquals(listOf("w1", "w2", "p1"), r.map { it.alvoId })
    }

    @Test
    fun `fila vazia nao quebra`() {
        assertTrue(CompactadorDeOutbox.compactar(emptyList()).isEmpty())
    }
}
