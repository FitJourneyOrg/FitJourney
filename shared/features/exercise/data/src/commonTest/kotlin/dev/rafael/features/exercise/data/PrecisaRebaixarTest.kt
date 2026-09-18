package dev.rafael.features.exercise.data

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Quando o catálogo vai à rede (fatia H, ARCH #37 e #30).
 *
 * ## Por que esta regra tem teste próprio
 *
 * Ela é a única parte do `refresh` que não é encanamento, e é a que errou. O defeito que a fatia H
 * conserta era **uma condição faltando**, não uma chamada errada: o cache guardava um idioma por
 * vez e a decisão de reusá-lo nunca perguntava qual.
 *
 * Testar isso pelo repositório exigiria `SyncStamps` (que recebe o banco), o SQLDelight e um HTTP
 * falso — três peças de infraestrutura para exercitar quatro booleanos.
 *
 * > **Regra que decide errado não precisa de banco para ser reproduzida; precisa de um nome e de
 * > uma assinatura.**
 */
class PrecisaRebaixarTest {

    /** O estado normal de quem abriu o app duas vezes no mesmo dia, no mesmo idioma. */
    private fun tudoEmOrdem(
        forcar: Boolean = false,
        carimboFresco: Boolean = true,
        idiomaGuardado: String? = "pt-BR",
        idiomaAtual: String = "pt-BR",
        catalogoVazio: Boolean = false,
    ) = precisaRebaixar(forcar, carimboFresco, idiomaGuardado, idiomaAtual, catalogoVazio)

    @Test
    fun `cache fresco no idioma certo nao vai a rede`() {
        assertFalse(tudoEmOrdem(), "963 linhas baixadas à toa a cada abertura")
    }

    @Test
    fun `carimbo vencido rebaixa`() {
        assertTrue(tudoEmOrdem(carimboFresco = false))
    }

    @Test
    fun `catalogo vazio rebaixa mesmo com carimbo fresco`() {
        // Sem isto, um app que limpou os dados ficaria com a tela vazia até o TTL expirar.
        assertTrue(tudoEmOrdem(catalogoVazio = true))
    }

    @Test
    fun `pull-to-refresh sempre vai a rede`() {
        assertTrue(tudoEmOrdem(forcar = true), "o usuário pediu")
    }

    /**
     * ⭐ **A IDA: escolher inglês rebaixa, mesmo com tudo fresco em português.**
     *
     * É o defeito principal da fatia H. Antes dela, esta chamada devolvia `false` e a pessoa lia
     * "Supino reto" numa interface inglesa por até 24 horas.
     */
    @Test
    fun `trocar de idioma rebaixa`() {
        assertTrue(
            tudoEmOrdem(idiomaGuardado = "pt-BR", idiomaAtual = "en"),
            "trocou de idioma e o app reusaria o catálogo do idioma anterior",
        )
    }

    /**
     * ⭐ **A VOLTA, que é onde a primeira versão desta correção ainda falhava.**
     *
     * Carimbar por idioma resolve a ida: `exercises:en` nunca foi marcado, logo rebaixa. Mas ao
     * VOLTAR para o português o carimbo `exercises:pt-BR` continua fresco de ontem e a tabela não
     * está vazia — e o app pularia a rede com o catálogo em inglês dentro.
     *
     * O mesmo defeito, só no caminho que ninguém testa à mão.
     *
     * > **Carimbo diz quando foi baixado. Ele não diz o que está guardado.**
     */
    @Test
    fun `voltar ao idioma anterior tambem rebaixa, com carimbo fresco`() {
        assertTrue(
            precisaRebaixar(
                forcar = false,
                carimboFresco = true,          // `exercises:pt-BR`, marcado ontem
                idiomaGuardado = "en",         // mas o que está no banco é inglês
                idiomaAtual = "pt-BR",
                catalogoVazio = false,
            ),
            "carimbo fresco do idioma certo com o catálogo do idioma ERRADO no banco",
        )
    }

    /**
     * App atualizado de uma versão anterior à fatia H: o banco tem catálogo e ninguém registrou
     * idioma. Rebaixar é a única resposta honesta — não dá para adivinhar em que idioma ele está.
     */
    @Test
    fun `sem idioma registrado rebaixa`() {
        assertTrue(tudoEmOrdem(idiomaGuardado = null))
    }
}
