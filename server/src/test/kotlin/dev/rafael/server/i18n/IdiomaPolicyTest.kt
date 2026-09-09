package dev.rafael.server.i18n

import dev.rafael.contract.i18n.Idioma
import dev.rafael.contract.i18n.IdiomaPolicy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * A cadeia de fallback de idioma (G.1, ARCH #37).
 *
 * ## Por que este arquivo existe com apenas DOIS idiomas
 *
 * Com dois, quase tudo cai em `pt-BR` e daria para não testar nada. O ponto é o contrário: a regra
 * é escrita e verificada **agora**, enquanto é barata, porque adicionar o terceiro idioma só
 * funciona se ela já estiver certa. Um idioma novo é uma constante no enum, e nenhum destes testes
 * muda.
 *
 * > **Idioma que não existe precisa cair em algum lugar decidido, ou cai onde der.**
 *
 * O que este arquivo NÃO testa: se a tradução está boa. Isso nenhum teste alcança.
 */
class IdiomaPolicyTest {

    // ---- de(): entrada de SISTEMA, tolera tudo ----

    @Test
    fun `tag exata devolve o idioma`() {
        assertEquals(Idioma.PT_BR, IdiomaPolicy.de("pt-BR"))
        assertEquals(Idioma.EN, IdiomaPolicy.de("en"))
    }

    /**
     * ⭐ A regra que faz sentido só quando há mais de uma região por língua.
     *
     * `pt-PT` é português, e mandar um português europeu para o inglês porque a região não bate
     * seria pior que a diferença de vocabulário entre as duas variantes.
     */
    @Test
    fun `regiao diferente cai na mesma lingua`() {
        assertEquals(Idioma.PT_BR, IdiomaPolicy.de("pt-PT"))
        assertEquals(Idioma.PT_BR, IdiomaPolicy.de("pt"))
        assertEquals(Idioma.EN, IdiomaPolicy.de("en-US"))
        assertEquals(Idioma.EN, IdiomaPolicy.de("en-GB"))
    }

    /** Língua que não existe cai no piso. É o caso do aparelho em francês. */
    @Test
    fun `lingua desconhecida cai no padrao`() {
        assertEquals(Idioma.PADRAO, IdiomaPolicy.de("fr-CA"))
        assertEquals(Idioma.PADRAO, IdiomaPolicy.de("ja"))
    }

    /**
     * O mundo real manda tag suja, e nenhuma dessas sujeiras justifica derrubar a requisição.
     *
     * O sublinhado é o formato do `java.util.Locale.toString()`, e é o que chega quando alguém
     * grava direto por script sem passar pelo app.
     */
    @Test
    fun `tolera sublinhado, caixa e espaco`() {
        assertEquals(Idioma.PT_BR, IdiomaPolicy.de("pt_BR"))
        assertEquals(Idioma.PT_BR, IdiomaPolicy.de("PT-br"))
        assertEquals(Idioma.EN, IdiomaPolicy.de("  en  "))
    }

    @Test
    fun `nulo e vazio caem no padrao`() {
        assertEquals(Idioma.PADRAO, IdiomaPolicy.de(null))
        assertEquals(Idioma.PADRAO, IdiomaPolicy.de(""))
        assertEquals(Idioma.PADRAO, IdiomaPolicy.de("   "))
    }

    // ---- preferido(): a lista do aparelho, em ORDEM ----

    /**
     * ⭐ A segunda escolha da pessoa vale mais que o nosso padrão.
     *
     * O Android entrega as preferências em ordem. Olhar só a primeira e cair no piso jogaria fora
     * uma vontade **explícita** por um valor default, e neste exemplo o app tem exatamente o idioma
     * que ela pôs em segundo lugar.
     */
    @Test
    fun `usa a primeira tag da lista que o app tem`() {
        assertEquals(Idioma.EN, IdiomaPolicy.preferido(listOf("fr-CA", "en-US", "pt-BR")))
    }

    @Test
    fun `a ordem da lista decide, nao a ordem do enum`() {
        assertEquals(Idioma.EN, IdiomaPolicy.preferido(listOf("en", "pt-BR")))
        assertEquals(Idioma.PT_BR, IdiomaPolicy.preferido(listOf("pt-BR", "en")))
    }

    @Test
    fun `lista sem nenhum idioma suportado cai no padrao`() {
        assertEquals(Idioma.PADRAO, IdiomaPolicy.preferido(listOf("fr", "de", "ja")))
        assertEquals(Idioma.PADRAO, IdiomaPolicy.preferido(emptyList()))
    }

    // ---- valida(): entrada de USUÁRIO, recusa ----

    /**
     * ⭐ O par que dá sentido ao arquivo: **a mesma tag, dois resultados**.
     *
     * `es` vindo do aparelho vira português, porque a alternativa é não mostrar nada. `es` vindo de
     * um `PATCH /me` é recusa, porque quem escolhe numa lista só pode escolher o que está na lista,
     * e um `200 OK` faria a pessoa acreditar que escolheu espanhol.
     *
     * > **Entrada de sistema tolera, entrada de usuário recusa.** Uma função só para os dois casos
     * > escolheria errado em um deles.
     *
     * Separar em dois testes deixaria cada um parecendo arbitrário. Juntos, um explica o outro.
     */
    @Test
    fun `idioma nao suportado e tolerado pelo sistema e recusado pelo usuario`() {
        assertEquals(Idioma.PADRAO, IdiomaPolicy.de("es"), "veio do aparelho: mostra em português")
        assertNull(IdiomaPolicy.valida("es"), "veio do PATCH: recusa")
    }

    /**
     * `valida` NÃO faz fallback de língua, e `de` faz.
     *
     * Pelo mesmo motivo do teste acima: a tela de idioma oferece `pt-BR`, não `pt-PT`. Aceitar uma
     * tag que não está na lista significa que o cliente mandou algo que não devia, e engolir isso
     * esconderia um defeito de cliente.
     */
    @Test
    fun `valida recusa a variante de regiao que de aceitaria`() {
        assertEquals(Idioma.PT_BR, IdiomaPolicy.de("pt-PT"))
        assertNull(IdiomaPolicy.valida("pt-PT"))
    }

    @Test
    fun `valida aceita a tag exata, com caixa e sublinhado tolerados`() {
        assertEquals(Idioma.PT_BR, IdiomaPolicy.valida("pt-BR"))
        assertEquals(Idioma.PT_BR, IdiomaPolicy.valida("pt_br"))
        assertEquals(Idioma.EN, IdiomaPolicy.valida("EN"))
        assertNull(IdiomaPolicy.valida(null))
    }

    // ---- o enum ----

    /**
     * O padrão é o idioma de ORIGEM, e não uma escolha arbitrária.
     *
     * Todo texto do app nasce em português e as traduções derivam dele, então só o pt-BR tem
     * garantia de estar completo. Um piso de fallback incompleto teria frases faltando por
     * definição.
     */
    @Test
    fun `o padrao e o idioma de origem`() {
        assertEquals(Idioma.PT_BR, Idioma.PADRAO)
    }

    @Test
    fun `a lingua ignora a regiao`() {
        assertEquals("pt", Idioma.PT_BR.lingua)
        assertEquals("en", Idioma.EN.lingua)
    }
}
