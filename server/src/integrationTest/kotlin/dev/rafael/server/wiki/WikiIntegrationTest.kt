package dev.rafael.server.wiki

import dev.rafael.contract.i18n.Idioma
import dev.rafael.core.result.AppResult
import dev.rafael.server.BancoDeTeste
import dev.rafael.server.features.wiki.db.WikiArticleTranslationsTable
import dev.rafael.server.features.wiki.db.WikiArticlesTable
import dev.rafael.server.features.wiki.db.WikiRepositoryImpl
import dev.rafael.server.features.wiki.models.WikiArticle
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDateTime
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import kotlin.uuid.Uuid

/**
 * O acervo do "Aprender" contra Postgres REAL (Fase 8, V61).
 *
 * ## Por que aqui, e não com fake
 *
 * Pela mesma razão escrita no `CatalogoTraduzidoIntegrationTest`: o que pode quebrar neste recurso
 * **é todo de SQL, e nada disso existe no Kotlin**.
 *
 * 1. o `LEFT` do join — com `INNER`, artigo sem tradução sumiria do acervo em inglês;
 * 2. o **índice único parcial**, que é a única coisa que garante um destaque só;
 * 3. os `CHECK` de categoria e de locale, que valem para escrita DIRETA — e é por escrita direta
 *    (o `R__wiki_conteudo.sql`) que o conteúdo entra.
 *
 * ## Isolamento por CHAVE, e `wiki_articles` NÃO é truncada
 *
 * Igual a `exercises`, esta tabela é conteúdo carregado por migration, não dado de teste — então
 * ela fica fora do `TRUNCATE` do [BancoDeTeste]. O que este arquivo cria, este arquivo apaga
 * (`ON DELETE CASCADE` da V61 leva a tradução junto).
 *
 * ## O destaque é um recurso SINGLETON, e o teste toma emprestado
 *
 * O índice parcial permite UM `featured = true` em todo o banco. Quando o conteúdo real existir,
 * ele terá o dele — e um teste que inserisse o seu falharia por colisão, sem relação nenhuma com o
 * que ele afirma. Por isso a classe libera o destaque no [setup] e devolve no [restaurarDestaque]:
 * o recurso é emprestado, não disputado.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class WikiIntegrationTest {

    private val repo = WikiRepositoryImpl()
    private val criados = mutableListOf<Uuid>()

    /** O destaque real que existia antes da classe rodar, para devolver no fim. */
    private var destaqueEmprestado: Uuid? = null

    @BeforeAll
    fun setup() {
        BancoDeTeste.dataSource
        BancoDeTeste.limpar()
        transaction {
            destaqueEmprestado = WikiArticlesTable.selectAll()
                .where { WikiArticlesTable.featured eq true }
                .map { it[WikiArticlesTable.id] }
                .singleOrNull()
            destaqueEmprestado?.let { id ->
                WikiArticlesTable.update({ WikiArticlesTable.id eq id }) { it[featured] = false }
            }
        }
    }

    @AfterAll
    fun restaurarDestaque() {
        transaction {
            destaqueEmprestado?.let { id ->
                WikiArticlesTable.update({ WikiArticlesTable.id eq id }) { it[featured] = true }
            }
        }
    }

    @AfterEach
    fun limparSinteticos() {
        if (criados.isEmpty()) return
        transaction { WikiArticlesTable.deleteWhere { WikiArticlesTable.id inList criados } }
        criados.clear()
    }

    // ---- helpers ----

    private fun inserir(
        slug: String,
        titulo: String = "Titulo pt",
        corpo: String = "Corpo pt",
        categoria: String = "TRAINING",
        destaque: Boolean = false,
        ordem: Int = 0,
    ): Uuid {
        val novo = Uuid.random()
        transaction {
            WikiArticlesTable.insert {
                it[id] = novo
                it[WikiArticlesTable.slug] = slug
                it[category] = categoria
                it[title] = titulo
                it[body] = corpo
                it[featured] = destaque
                it[orderIndex] = ordem
                it[updatedAt] = LocalDateTime(2026, 6, 15, 0, 0)
            }
        }
        criados += novo
        return novo
    }

    private fun traduzir(artigo: Uuid, locale: String, titulo: String, corpo: String) {
        transaction {
            WikiArticleTranslationsTable.insert {
                it[articleId] = artigo
                it[WikiArticleTranslationsTable.locale] = locale
                it[title] = titulo
                it[body] = corpo
            }
        }
    }

    private fun meus(lista: List<WikiArticle>, prefixo: String) =
        lista.filter { it.slug.startsWith(prefixo) }

    // ---- o seed repetível ----

    /**
     * ⭐ **O `R__wiki_conteudo.sql` rodou de verdade.**
     *
     * A guarda `IF to_regclass('public.wiki_articles') IS NULL THEN RETURN` que o seed ganhou em
     * 2026-09-30 conserta um defeito real (migration repetível roda em migrate PARCIAL, e o
     * `DisplayNameBackfillIntegrationTest` migra até a V34), mas cria um modo de falha
     * **silencioso**: com a tabela ausente, o acervo simplesmente não carrega, sem erro nenhum.
     * Em produção isso apareceria como a tela "Aprender" vazia, e ninguém saberia por quê.
     *
     * > **Guarda que transforma erro em silêncio precisa de alguém escutando.**
     *
     * Afirma sobre o acervo REAL — as linhas que este arquivo não criou —, e não sobre um slug
     * específico: renomear um artigo é edição de conteúdo e não pode quebrar teste. Se um dia o
     * acervo nascer vazio de propósito, este teste cai, e cair é o comportamento certo: acervo
     * vazio tem de ser decisão, não acidente.
     */
    @Test
    fun `o seed repetivel carregou o acervo real`() {
        val doSeed = transaction {
            WikiArticlesTable.selectAll()
                .map { it[WikiArticlesTable.slug] }
                .filterNot { it.startsWith("teste-") }
        }

        assertTrue(
            doSeed.isNotEmpty(),
            "R__wiki_conteudo.sql não carregou artigo nenhum -- a guarda de existência pulou em silêncio?",
        )
    }

    /**
     * ⚠️ **Artigo sem tradução em inglês quebra o build, e isto é uma REGRA DE PRODUTO.**
     *
     * O `LEFT JOIN` da V61 tolera tradução ausente de propósito: ele serve o piso pt-BR e a tela
     * funciona. O problema é que funciona **calado** -- quem usa o app em inglês lê português e
     * nada no sistema reclama. Foi exatamente o buraco que a tradução dos 5 artigos fechou.
     *
     * > **Falha silenciosa não vira defeito: vira costume.**
     *
     * O custo é real e aceito: publicar artigo novo exige escrever as duas versões. Se um dia a
     * decisão for permitir artigo só em português, este teste é a linha que se apaga.
     */
    @Test
    fun `todo artigo do acervo real tem traducao em ingles`() {
        val semIngles = transaction {
            val traduzidos = WikiArticleTranslationsTable
                .selectAll()
                .filter { it[WikiArticleTranslationsTable.locale] == "en" }
                .map { it[WikiArticleTranslationsTable.articleId] }
                .toSet()

            WikiArticlesTable.selectAll()
                .filterNot { it[WikiArticlesTable.slug].startsWith("teste-") }
                .filterNot { it[WikiArticlesTable.id] in traduzidos }
                .map { it[WikiArticlesTable.slug] }
        }

        assertTrue(
            semIngles.isEmpty(),
            "sem tradução em inglês: $semIngles -- quem abrir o app em inglês vai ler português " +
                "sem nenhum aviso. Escreva conteudo/aprender/en/<slug>.md e rode o gen_wiki.py.",
        )
    }

    // ---- tradução ----

    @Test
    fun `artigo sem traducao aparece no piso em vez de sumir do acervo`() = runBlocking {
        inserir(slug = "teste-sem-traducao", titulo = "Como progredir carga", corpo = "Corpo em portugues")

        val artigos = (repo.findAll(Idioma.EN) as AppResult.Success).value
        val meu = meus(artigos, "teste-sem-traducao").single()

        // Com INNER em vez de LEFT, este artigo simplesmente não estaria na lista -- a pessoa
        // trocaria de idioma e o app perderia conteúdo, sem erro nenhum.
        assertEquals("Como progredir carga", meu.title)
        assertEquals("Corpo em portugues", meu.body)
    }

    @Test
    fun `artigo traduzido devolve titulo e corpo do idioma pedido`() = runBlocking {
        val id = inserir(slug = "teste-traduzido", titulo = "Descanso entre series", corpo = "Corpo pt")
        traduzir(id, "en", "Rest between sets", "Body in English")

        val artigos = (repo.findAll(Idioma.EN) as AppResult.Success).value
        val meu = meus(artigos, "teste-traduzido").single()

        assertEquals("Rest between sets", meu.title)
        assertEquals("Body in English", meu.body, "título e corpo caem juntos, nunca meio a meio")
    }

    @Test
    fun `no piso a traducao e ignorada`() = runBlocking {
        val id = inserir(slug = "teste-piso", titulo = "Titulo pt-BR", corpo = "Corpo pt-BR")
        traduzir(id, "en", "English title", "English body")

        val artigos = (repo.findAll(Idioma.PT_BR) as AppResult.Success).value
        val meu = meus(artigos, "teste-piso").single()

        // `PADRAO` não faz join: `wiki_articles.title` JÁ é o pt-BR (V61).
        assertEquals("Titulo pt-BR", meu.title)
    }

    // ---- ordenação ----

    @Test
    fun `destaque vem primeiro, e depois vale a ordem editorial`() = runBlocking {
        inserir(slug = "teste-ordem-c", ordem = 3)
        inserir(slug = "teste-ordem-a", ordem = 1)
        inserir(slug = "teste-ordem-destaque", destaque = true, ordem = 99)
        inserir(slug = "teste-ordem-b", ordem = 2)

        val artigos = (repo.findAll(Idioma.PT_BR) as AppResult.Success).value

        assertEquals(
            listOf("teste-ordem-destaque", "teste-ordem-a", "teste-ordem-b", "teste-ordem-c"),
            meus(artigos, "teste-ordem-").map { it.slug },
            "o destaque ignora o order_index; o resto o respeita",
        )
    }

    // ---- invariantes de schema (caminhos de falha) ----

    @Test
    fun `dois destaques nao podem coexistir`() {
        inserir(slug = "teste-destaque-1", destaque = true)

        val segundo = runCatching { inserir(slug = "teste-destaque-2", destaque = true) }

        assertTrue(segundo.isFailure, "o índice único parcial tem de recusar o segundo destaque")
        val quantos = transaction {
            WikiArticlesTable.selectAll().where { WikiArticlesTable.featured eq true }.count()
        }
        assertEquals(1L, quantos, "no máximo um destaque em todo o acervo")
    }

    @Test
    fun `slug repetido e recusado`() {
        inserir(slug = "teste-slug-unico")

        val repetido = runCatching { inserir(slug = "teste-slug-unico") }

        // O slug é a chave que o seed usa para casar a linha e que um deep link usaria para achar
        // o artigo. Dois iguais fariam o UPSERT escrever no artigo errado.
        assertTrue(repetido.isFailure, "slug duplicado tem de estourar na escrita, não na leitura")
    }

    @Test
    fun `categoria fora do vocabulario e recusada`() {
        val invalida = runCatching { inserir(slug = "teste-categoria", categoria = "CIENTIFICO") }

        // O CHECK espelha o enum `WikiCategory`. É ele que protege a escrita DIRETA -- que é
        // justamente por onde o conteúdo entra (o seed repetível).
        assertTrue(invalida.isFailure, "categoria fora do enum tem de ser recusada pelo banco")
    }

    @Test
    fun `locale fora do vocabulario e recusado`() {
        val id = inserir(slug = "teste-locale")

        val invalido = runCatching { traduzir(id, "es", "Titulo es", "Cuerpo es") }

        assertTrue(invalido.isFailure, "idioma novo exige migration, não só INSERT")
    }
}
