package dev.rafael.server.media

import dev.rafael.core.result.AppResult
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ArmazenamentoEmDiscoTest {

    private val raiz: File = createTempDirectory("midia").toFile()
    private val armazem = ArmazenamentoEmDisco(raiz)

    @AfterTest fun limpar() { raiz.deleteRecursively() }

    private fun <T> ok(r: AppResult<T>): T = (r as AppResult.Success).value

    @Test
    fun `guarda e le os mesmos bytes`() = runBlocking {
        val bytes = byteArrayOf(1, 2, 3, 4, 5)
        val ref = ok(armazem.guardar(bytes, "jpg"))
        assertContentEquals(bytes, ok(armazem.ler(ref)))
    }

    @Test
    fun `duas gravacoes nunca colidem`() = runBlocking {
        val a = ok(armazem.guardar(byteArrayOf(1), "jpg"))
        val b = ok(armazem.guardar(byteArrayOf(2), "jpg"))
        assertTrue(a != b)
    }

    @Test
    fun `referencia inexistente devolve null, nao erro`() = runBlocking {
        // "A foto sumiu" é resposta legítima: depois de 90 dias (4.8) a referência continua no
        // banco e o arquivo não existe mais. Se isso virasse falha, o feed quebraria com o tempo.
        assertNull(ok(armazem.ler("ab/cd/${"0".repeat(32)}.jpg")))
    }

    @Test
    fun `apagar e idempotente`() = runBlocking {
        // A purga dos 90 dias e a cascata do grupo podem passar pela mesma linha duas vezes.
        val ref = ok(armazem.guardar(byteArrayOf(9), "jpg"))
        ok(armazem.apagar(ref))
        ok(armazem.apagar(ref))
        assertNull(ok(armazem.ler(ref)))
    }

    // ---- travessia de caminho ----

    @Test
    fun `referencia com salto de pasta nao le fora da raiz`() = runBlocking {
        // A referência vem do BANCO, e um dia alguém vai passar uma vinda de outro lugar. Sem esta
        // guarda, `../..` transforma a rota da foto num leitor de arquivos do servidor.
        val segredo = File(raiz.parentFile, "segredo.txt").apply { writeText("nao devia vazar") }
        try {
            assertNull(ok(armazem.ler("../segredo.txt")))
            assertNull(ok(armazem.ler("ab/cd/../../../segredo.txt")))
            assertTrue(segredo.exists(), "e apagar também não pode alcançar")
            ok(armazem.apagar("../segredo.txt"))
            assertTrue(segredo.exists())
        } finally {
            segredo.delete()
        }
    }

    @Test
    fun `caminho absoluto e recusado`() = runBlocking {
        assertNull(ok(armazem.ler("/etc/passwd")))
    }

    @Test
    fun `formato fora do padrao e recusado`() = runBlocking {
        assertNull(ok(armazem.ler("qualquer-coisa.jpg")))
        assertNull(ok(armazem.ler("ab/cd/NAOEHEX.jpg")))
    }
}
