package dev.rafael.app.ui

import dev.rafael.contract.stats.ConquistaIds
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * O catálogo de textos das conquistas (G.5, ARCH #37).
 *
 * ## O que este arquivo impede
 *
 * A G.5 trouxe 18 frases do servidor para o cliente. O modo de essa mudança apodrecer é o mesmo que
 * o `TextosDeErroTest` já vigia para os erros, e aqui é **mais silencioso**: sem texto, a tela
 * **pula a medalha**. Não sobra fallback, não sobra frase em português, não sobra nada — some um
 * cartão da grade e ninguém percebe até alguém contar as medalhas.
 *
 * > **Medalha que o servidor concede e a tela não mostra é pior que medalha que não existe.**
 *
 * ## A corrente tem três elos, e cada teste guarda um
 *
 * | elo | onde | quem guarda |
 * |---|---|---|
 * | enum do servidor ↔ vocabulário do contrato | `:server` | `AchievementPolicyTest` |
 * | vocabulário do contrato ↔ `TextosDeConquista` | aqui | este arquivo |
 * | `TextosDeConquista` ↔ `strings.xml` | aqui | a chave derivada, nos dois sentidos |
 *
 * Nenhum dos três sozinho basta: com só o primeiro, o id existe e ninguém escreveu a frase; com só
 * o terceiro, as frases estão certas para um conjunto de ids que pode não ser o do servidor.
 */
class TextosDeConquistaTest {

    /** A chave do `strings.xml` que corresponde a um id, pela convenção do arquivo. */
    private fun chaveDoTitulo(id: String) = "conquista_${id.lowercase()}_titulo"

    private fun chaveDaDescricao(id: String) = "conquista_${id.lowercase()}_descricao"

    /** Todas as chaves `conquista_*` que existem no catálogo pt-BR. */
    private val noCatalogo: Set<String>
        get() = CatalogoDeStrings.entradas.map { it.chave }.filter { it.startsWith("conquista_") }.toSet()

    /**
     * ⭐ **Toda conquista tem título e descrição, sem exceção declarada.**
     *
     * Diferente do [TextosDeErro], aqui NÃO existe lista de exceções, e isso é escolha: o conjunto
     * tem nove itens e é do produto inteiro, não de uma área. Uma medalha sem nome não é débito
     * aceitável em nenhuma leitura — ela simplesmente não aparece.
     */
    @Test
    fun `toda conquista tem titulo e descricao`() {
        val semTitulo = ConquistaIds.TODOS.filter { TextosDeConquista.titulo(it) == null }
        assertTrue(
            semTitulo.isEmpty(),
            "conquista sem título no cliente: $semTitulo. " +
                "Acrescente o `when` em TextosDeConquista.titulo e a chave no strings.xml.",
        )

        val semDescricao = ConquistaIds.TODOS.filter { TextosDeConquista.descricao(it) == null }
        assertTrue(semDescricao.isEmpty(), "conquista sem descrição no cliente: $semDescricao")
    }

    /**
     * ⭐ **A chave no `strings.xml` é DERIVADA do id, e o build confere.**
     *
     * `titulo()` devolve um `Int`, e num teste JVM um `R.string.*` é um número sem o texto: um id
     * errado é indistinguível de um certo lendo o código. A ponte é a convenção
     * (`PRIMEIRO_TREINO` → `conquista_primeiro_treino_titulo`), e convenção só vale verificada.
     *
     * Este é o teste que teria pego, na G.3, o erro que só apareceu na tela.
     */
    @Test
    fun `cada id tem as duas chaves derivadas no catalogo`() {
        val faltando = ConquistaIds.TODOS
            .flatMap { listOf(chaveDoTitulo(it), chaveDaDescricao(it)) }
            .filter { it !in noCatalogo }

        assertTrue(
            faltando.isEmpty(),
            "chave derivada de um id de conquista e ausente do strings.xml: $faltando",
        )
    }

    /**
     * O caminho inverso: **chave `conquista_*` sobrando no catálogo**.
     *
     * Sobra quando alguém apaga uma conquista do servidor e esquece a frase aqui. O tradutor
     * recebe duas linhas que ninguém vai ler, e — pior — a lista de strings passa a superestimar o
     * trabalho. É a mesma metade que o `TextosDeErroTest` já cobra para os erros.
     */
    @Test
    fun `nenhuma chave de conquista sobra no catalogo`() {
        val esperadas = ConquistaIds.TODOS
            .flatMap { listOf(chaveDoTitulo(it), chaveDaDescricao(it)) }
            .toSet()

        val sobrando = noCatalogo - esperadas

        assertTrue(
            sobrando.isEmpty(),
            "chave `conquista_*` sem id correspondente em ConquistaIds: $sobrando",
        )
    }

    /**
     * **Título e descrição dizem coisas diferentes.**
     *
     * O par do `NIVEL_5` é o que mais se aproxima de colidir — *"Nível 5"* e *"Alcance o nível 5"* —
     * e é justamente ali que uma tradução apressada pode devolver a mesma frase duas vezes. O
     * cartão ficaria com a linha repetida, em negrito e em cinza.
     *
     * Compara o TEXTO, e não os ids: dois ids diferentes apontando para o mesmo valor é exatamente
     * a regressão procurada, e comparar `Int` não a veria.
     */
    @Test
    fun `o titulo nunca repete a descricao`() {
        val repetidos = ConquistaIds.TODOS.filter { id ->
            val t = CatalogoDeStrings.porChave[chaveDoTitulo(id)]
            val d = CatalogoDeStrings.porChave[chaveDaDescricao(id)]
            t != null && t == d
        }

        assertTrue(repetidos.isEmpty(), "título igual à descrição em: $repetidos")
    }

    /**
     * Id desconhecido devolve `null` nas duas, que é o caminho de app antigo com servidor novo.
     *
     * Parece óbvio e é o `else` do `when` — mas é ele que decide se uma conquista nova derruba a
     * tela ou só não aparece nela, e trocá-lo por `error(...)` num dia de faxina seria uma linha
     * plausível.
     */
    @Test
    fun `id desconhecido nao tem texto e nao estoura`() {
        assertEquals(null, TextosDeConquista.titulo("CONQUISTA_DO_FUTURO"))
        assertEquals(null, TextosDeConquista.descricao("CONQUISTA_DO_FUTURO"))
    }
}
