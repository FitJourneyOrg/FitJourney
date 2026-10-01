package dev.rafael.features.wiki.data

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A decisão de ir à rede, sem banco e sem HTTP.
 *
 * O defeito que esta função existe para impedir não é de I/O, é de **condição incompleta** — foi
 * assim que o catálogo de exercícios serviu 963 linhas em inglês por 24h depois que a pessoa
 * voltou para o português, sem erro e sem log.
 */
class PrecisaRebaixarTest {

    @Test
    fun `carimbo fresco, mesmo idioma e acervo cheio dispensa a rede`() {
        assertFalse(
            precisaRebaixar(
                forcar = false, carimboFresco = true,
                idiomaGuardado = "pt-BR", idiomaAtual = "pt-BR", acervoVazio = false,
            ),
        )
    }

    @Test
    fun `carimbo vencido rebaixa`() {
        assertTrue(
            precisaRebaixar(
                forcar = false, carimboFresco = false,
                idiomaGuardado = "pt-BR", idiomaAtual = "pt-BR", acervoVazio = false,
            ),
        )
    }

    @Test
    fun `acervo vazio ignora o carimbo`() {
        // Sem dado não há o que preservar -- e é este caso que faz a primeira abertura baixar.
        assertTrue(
            precisaRebaixar(
                forcar = false, carimboFresco = true,
                idiomaGuardado = "pt-BR", idiomaAtual = "pt-BR", acervoVazio = true,
            ),
        )
    }

    @Test
    fun `trocar de idioma rebaixa mesmo com carimbo fresco`() {
        assertTrue(
            precisaRebaixar(
                forcar = false, carimboFresco = true,
                idiomaGuardado = "pt-BR", idiomaAtual = "en", acervoVazio = false,
            ),
        )
    }

    /**
     * ⭐ A VOLTA — a metade que passa despercebida.
     *
     * Carimbar por idioma resolve a ida. Voltando ao português, o carimbo `wiki:pt-BR` ainda está
     * fresco de ontem e a tabela não está vazia: só o idioma GUARDADO denuncia que o que está lá
     * dentro é inglês.
     *
     * > **Carimbo diz QUANDO foi baixado. Ele não diz O QUE está guardado.**
     */
    @Test
    fun `voltar ao idioma anterior tambem rebaixa, porque o guardado e outro`() {
        assertTrue(
            precisaRebaixar(
                forcar = false, carimboFresco = true,
                idiomaGuardado = "en", idiomaAtual = "pt-BR", acervoVazio = false,
            ),
        )
    }

    @Test
    fun `aparelho que nunca baixou rebaixa`() {
        assertTrue(
            precisaRebaixar(
                forcar = false, carimboFresco = true,
                idiomaGuardado = null, idiomaAtual = "pt-BR", acervoVazio = false,
            ),
        )
    }

    @Test
    fun `pull-to-refresh do usuario ignora tudo`() {
        assertTrue(
            precisaRebaixar(
                forcar = true, carimboFresco = true,
                idiomaGuardado = "pt-BR", idiomaAtual = "pt-BR", acervoVazio = false,
            ),
        )
    }
}
