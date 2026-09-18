package dev.rafael.server.features.notificacao.services

import dev.rafael.contract.i18n.Idioma
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/**
 * O catálogo de texto do servidor (G.1, ARCH #37).
 *
 * ## O que este arquivo NÃO testa, e por quê
 *
 * **Não testa que toda chave existe em todos os idiomas.** Isso o compilador já garante: os `when`
 * do `TextosDeAviso` são exaustivos sobre a `ChaveDeAviso` sealed, então um aviso novo quebra o
 * build em todos os idiomas que ainda não o traduziram, e um idioma novo quebra o `render`.
 *
 * > **Verificação que o compilador faz não precisa de teste, e não pode ser esquecida.**
 *
 * Escrever esse teste assim mesmo daria a falsa impressão de que ele é a rede de proteção, e alguém
 * poderia trocar o sealed por enum mais mapa achando que estava coberto.
 *
 * O que sobra para cá é o que o compilador não vê: **plural, possessivo e convenção de escrita.**
 */
class TextosDeAvisoTest {

    private val grupo = "Setembro sem desculpa"

    // ---- plural ----

    /**
     * ⭐ O plural é decidido por idioma, e não herdado do português.
     *
     * Português e inglês concordam em ter duas formas. **Concordar por acaso não é motivo para
     * compartilhar código**: o terceiro idioma pode ter quatro, e um plural centralizado numa
     * função "genérica" seria reescrito no dia em que ele entrasse.
     */
    @Test
    fun `entradas do dia pluralizam nos dois idiomas`() {
        val um = ChaveDeAviso.EntradasDoDia(grupo, quantas = 1, souOCriador = false)
        val varios = ChaveDeAviso.EntradasDoDia(grupo, quantas = 3, souOCriador = false)

        assertTrue(TextosDeAviso.render(um, Idioma.PT_BR).corpo.startsWith("1 pessoa entrou"))
        assertTrue(TextosDeAviso.render(varios, Idioma.PT_BR).corpo.startsWith("3 pessoas entraram"))

        assertTrue(TextosDeAviso.render(um, Idioma.EN).corpo.startsWith("1 person joined"))
        assertTrue(TextosDeAviso.render(varios, Idioma.EN).corpo.startsWith("3 people joined"))
    }

    @Test
    fun `fila parada pluraliza nos dois idiomas`() {
        val um = ChaveDeAviso.FilaParada(grupo, casos = 1, dias = 4)
        val varios = ChaveDeAviso.FilaParada(grupo, casos = 2, dias = 4)

        assertEquals("Tem 1 caso parado há 4 dias.", TextosDeAviso.render(um, Idioma.PT_BR).corpo)
        assertEquals("Tem 2 casos parados há 4 dias.", TextosDeAviso.render(varios, Idioma.PT_BR).corpo)

        assertEquals("1 case has been waiting for 4 days.", TextosDeAviso.render(um, Idioma.EN).corpo)
        assertEquals("2 cases have been waiting for 4 days.", TextosDeAviso.render(varios, Idioma.EN).corpo)
    }

    // ---- o possessivo ----

    /**
     * ⭐ "Seu desafio" só para quem CRIOU. Foi defeito na bateria da fatia F.
     *
     * A primeira versão dizia "no seu desafio" para todo mundo, e para os 49 que entraram num
     * desafio alheio o possessivo é falso: eles participam, não são donos.
     *
     * O critério é quem criou, e não quem é admin hoje, porque o cargo é transferível (2.12).
     *
     * As duas metades ficam no MESMO teste de propósito: separadas, "consertar" uma delas quebraria
     * a outra em silêncio.
     */
    @Test
    fun `o possessivo sai so para o criador, nos dois idiomas`() {
        val criador = ChaveDeAviso.EntradasDoDia(grupo, quantas = 2, souOCriador = true)
        val membro = ChaveDeAviso.EntradasDoDia(grupo, quantas = 2, souOCriador = false)

        assertTrue(TextosDeAviso.render(criador, Idioma.PT_BR).corpo.contains("no seu desafio"))
        assertFalse(TextosDeAviso.render(membro, Idioma.PT_BR).corpo.contains("seu desafio"))

        assertTrue(TextosDeAviso.render(criador, Idioma.EN).corpo.contains("your challenge"))
        assertFalse(TextosDeAviso.render(membro, Idioma.EN).corpo.contains("your challenge"))
    }

    /** O título das entradas do dia é o NOME DO GRUPO, que é conteúdo de usuário e não se traduz. */
    @Test
    fun `nome de grupo atravessa os dois idiomas sem ser tocado`() {
        val chave = ChaveDeAviso.EntradasDoDia(grupo, quantas = 1, souOCriador = false)

        assertEquals(grupo, TextosDeAviso.render(chave, Idioma.PT_BR).titulo)
        assertEquals(grupo, TextosDeAviso.render(chave, Idioma.EN).titulo)
    }

    // ---- o que o texto NÃO pode dizer ----

    /**
     * ⭐ O aviso de invalidação não menciona denúncia, em nenhum idioma.
     *
     * Foi defeito da bateria da fatia F: o texto dizia "o admin avaliou a denúncia" e era reusado na
     * invalidação direta (6.10), onde denúncia nenhuma existe. Quem recebesse por aquele caminho
     * procuraria uma acusação que nunca houve.
     *
     * > **Mensagem compartilhada só pode afirmar o que é verdade em todos os caminhos que a usam.**
     *
     * Este teste existe para o defeito não voltar por tradução: é fácil um tradutor "melhorar" a
     * frase inglesa acrescentando *"the report"*, e nada mais no sistema perceberia.
     */
    @Test
    fun `check-in invalidado nao menciona denuncia em nenhum idioma`() {
        val chave = ChaveDeAviso.CheckInInvalidado(grupo)

        val pt = TextosDeAviso.render(chave, Idioma.PT_BR)
        assertFalse((pt.titulo + pt.corpo).lowercase().contains("denúncia"))

        val en = TextosDeAviso.render(chave, Idioma.EN)
        assertFalse((en.titulo + en.corpo).lowercase().contains("report"))
    }

    // ---- convenção de escrita ----

    /**
     * ⭐ **Sem travessão, em nenhum idioma.**
     *
     * É convenção de produto (#37, seção 8), e por isso é verificada em vez de combinada: quem
     * traduzir o sétimo idioma não vai ler o ADR, e um travessão que passa não quebra nada visível.
     *
     * > **Regra que depende de alguém lembrar já falhou.** É o argumento do Konsist aplicado a texto.
     *
     * Varre TODAS as chaves em TODOS os idiomas, então cobre o aviso e o idioma que ainda não
     * existem. Adicionar qualquer um dos dois entra nesta varredura sozinho.
     */
    @Test
    fun `nenhum texto usa travessao`() {
        val proibidos = listOf('—', '–')   // travessão e meia-risca

        Idioma.TODOS.forEach { idioma ->
            todasAsChaves().forEach { chave ->
                val t = TextosDeAviso.render(chave, idioma)
                proibidos.forEach { c ->
                    assertFalse(
                        t.titulo.contains(c) || t.corpo.contains(c),
                        "travessão em ${chave::class.simpleName} (${idioma.tag}): ${t.titulo} / ${t.corpo}",
                    )
                }
            }
        }
    }

    /** Nenhum texto sai vazio, em nenhum idioma. Corpo vazio vira push sem conteúdo na bandeja. */
    @Test
    fun `nenhum texto sai vazio`() {
        Idioma.TODOS.forEach { idioma ->
            todasAsChaves().forEach { chave ->
                val t = TextosDeAviso.render(chave, idioma)
                assertTrue(t.titulo.isNotBlank(), "título vazio em ${chave::class.simpleName} (${idioma.tag})")
                assertTrue(t.corpo.isNotBlank(), "corpo vazio em ${chave::class.simpleName} (${idioma.tag})")
            }
        }
    }

    /**
     * ⚠️ Lista mantida à mão, e é a única coisa aqui que pode ficar desatualizada.
     *
     * Kotlin não enumera as subclasses de uma `sealed interface` sem reflexão, e trazer reflexão
     * para um teste de texto custa mais do que resolve. A consequência é honesta: **um aviso novo
     * que não seja acrescentado aqui não é varrido** pelas duas verificações acima.
     *
     * O compilador continua obrigando a traduzi-lo, então o buraco é só de convenção, não de
     * existência. Fica anotado porque é exatamente o tipo de coisa que ninguém lembra.
     */
    private fun todasAsChaves(): List<ChaveDeAviso> = listOf(
        ChaveDeAviso.PedidoDeAmizade(nome = "Ana"),
        ChaveDeAviso.ComentarioNoMeuCheckIn(nome = "Ana", trecho = "boa!"),
        ChaveDeAviso.DenunciaContraMim(ehComentario = false),
        ChaveDeAviso.DenunciaContraMim(ehComentario = true),
        ChaveDeAviso.NovaDenunciaNoGrupo(grupo = grupo),
        ChaveDeAviso.CheckInInvalidado(grupo = grupo),
        ChaveDeAviso.EntradasDoDia(grupo, quantas = 1, souOCriador = true),
        ChaveDeAviso.EntradasDoDia(grupo, quantas = 5, souOCriador = false),
        ChaveDeAviso.FilaParada(grupo, casos = 1, dias = 3),
        ChaveDeAviso.FilaParada(grupo, casos = 4, dias = 3),
    )

    // ---- o tipo do deep link ----

    /**
     * O `tipo` é derivado da chave, e o cliente ramifica por ele.
     *
     * Antes era escrito à mão em cada factory, ao lado da chave: dois lugares dizendo a mesma coisa,
     * e nada impedindo que discordassem. Este teste guarda os VALORES, porque mudá-los quebra o deep
     * link de todo app já instalado, e isso não pode acontecer por refatoração.
     */
    @Test
    fun `o tipo do deep link nao muda por engano`() {
        assertEquals("PEDIDO_DE_AMIZADE", ChaveDeAviso.PedidoDeAmizade("Ana").tipo)
        assertEquals("COMENTARIO_NO_CHECKIN", ChaveDeAviso.ComentarioNoMeuCheckIn("Ana", "x").tipo)
        assertEquals("DENUNCIA_CONTRA_MIM", ChaveDeAviso.DenunciaContraMim(false).tipo)
        assertEquals("DENUNCIA_NO_GRUPO", ChaveDeAviso.NovaDenunciaNoGrupo(grupo).tipo)
        assertEquals("CHECK_IN_INVALIDADO", ChaveDeAviso.CheckInInvalidado(grupo).tipo)
        assertEquals("ENTRADAS_DO_DIA", ChaveDeAviso.EntradasDoDia(grupo, 1, false).tipo)
        assertEquals("FILA_PARADA", ChaveDeAviso.FilaParada(grupo, 1, 3).tipo)
    }

    /**
     * O corte do trecho do comentário acontece na factory, **não no catálogo**.
     *
     * Se cada idioma cortasse, o primeiro que esquecesse entregaria um push truncado pelo sistema no
     * meio de uma palavra. Cortando na factory, o limite é um só para todos os idiomas.
     */
    @Test
    fun `o trecho do comentario e cortado antes de chegar ao catalogo`() {
        val longo = "a".repeat(300)
        val aviso = Aviso.comentarioNoMeuCheckIn("Ana", longo, Uuid.random(), Uuid.random())

        val chave = aviso.chave as ChaveDeAviso.ComentarioNoMeuCheckIn
        assertTrue(chave.trecho.length < longo.length, "o corte não aconteceu")
        assertTrue(chave.trecho.endsWith("…"), "cortou sem avisar que cortou")

        // E o catálogo repassa o que recebeu, sem cortar de novo nem em pt nem em en.
        assertEquals(chave.trecho, TextosDeAviso.render(chave, Idioma.PT_BR).corpo)
        assertEquals(chave.trecho, TextosDeAviso.render(chave, Idioma.EN).corpo)
    }
}
