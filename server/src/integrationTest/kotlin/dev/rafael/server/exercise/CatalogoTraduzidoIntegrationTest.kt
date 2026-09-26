package dev.rafael.server.exercise

import dev.rafael.contract.i18n.Idioma
import dev.rafael.core.result.AppResult
import dev.rafael.server.BancoDeTeste
import dev.rafael.server.features.exercise.db.ExerciseRepositoryImpl
import dev.rafael.server.features.exercise.db.ExerciseTranslationsTable
import dev.rafael.server.features.exercise.db.ExercisesTable
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import kotlin.uuid.Uuid

/**
 * O catálogo traduzido, contra Postgres REAL (fatia H, ARCH #37).
 *
 * ## ⭐ Este arquivo É a guarda que a escolha da tabela custou
 *
 * A alternativa a `exercise_translations` era `name_en`, `name_es`, uma coluna por idioma. Ela
 * tinha uma vantagem que esta modelagem NÃO tem: o `when (idioma)` da leitura ficaria exaustivo, e
 * idioma novo no enum `Idioma` quebraria o build até alguém traduzir — o mesmo mecanismo do
 * `TextosDeAviso`, que o #37 celebra.
 *
 * Com mais de quatro idiomas a coluna sai cara demais, então a proteção mudou de dono: **saiu do
 * compilador e veio para cá**. É mais fraca de propósito, e a decisão está escrita na V49.
 *
 * > **Quando o tipo deixa de garantir, alguém tem de escrever o teste — e dizer, no teste, que ele
 * > está ali no lugar do compilador.**
 *
 * ## Por que contra o banco, e não com fake
 *
 * As três coisas que podem quebrar aqui são todas de SQL, e nenhuma existe no Kotlin:
 *
 * 1. o `LEFT` do join (com `INNER`, exercício sem tradução **sumiria da lista**);
 * 2. o `CHECK` de vocabulário, que só vale para escrita direta;
 * 3. a chave composta, que impede duas traduções do mesmo par.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CatalogoTraduzidoIntegrationTest {

    private val repo = ExerciseRepositoryImpl()

    @BeforeAll
    fun setup() {
        BancoDeTeste.dataSource
        BancoDeTeste.limpar()
    }

    /**
     * Isolamento por CHAVE (P0.1, 2026-09-22), não mais por limpeza da tabela inteira.
     *
     * Até aqui, cada teste apagava `exercise_translations` INTEIRA no `@BeforeEach` e pegava
     * `ExercisesTable.selectAll().limit(1).single()` -- o primeiro exercício REAL do catálogo.
     * Funcionava porque a tabela era dado de teste, sempre vazia no início. Virou referência (H.3,
     * V53 carregou ~923 traduções reais) -- apagar tudo a cada teste destruiria essa carga para
     * qualquer classe que rodasse DEPOIS no mesmo container compartilhado (ver `BancoDeTeste`), e
     * o exercício real "aleatório" já pode ter linha da V53, colidindo com a PK composta ao inserir.
     *
     * Cada teste que precisa inserir tradução cria o PRÓPRIO exercício sintético -- a mesma lição
     * de isolamento por chave do `AchievementGrantIntegrationTest`, só que com limpeza explícita no
     * fim (`limparSinteticos`): lá o `users` truncado pela PRÓXIMA classe bastava, aqui `exercises`
     * nunca é truncado (é catálogo real), então o que este arquivo cria, este arquivo apaga -- e o
     * `ON DELETE CASCADE` da FK (V49) leva a tradução junto, sem precisar tocar na outra tabela.
     *
     * O teste que conta o catálogo INTEIRO (`nenhum idioma fica traduzido pela metade`) não usa
     * isto -- ele quer justamente o estado REAL, sem sintético nenhum misturado. É por isso que a
     * limpeza é `@AfterEach`, não `@AfterAll`: `PER_CLASS` não garante a ORDEM dos testes, e um
     * sintético "vazando" de um teste anterior para dentro da contagem daquele inflaria o `total`
     * sem inflar nenhum idioma -- viraria falso positivo de "meio traduzido".
     */
    private val sinteticos = mutableListOf<Uuid>()

    private fun exercicioSintetico(): Uuid {
        val id = Uuid.random()
        transaction {
            ExercisesTable.insert {
                it[ExercisesTable.id] = id
                it[name] = "Exercício de teste $id"
                it[category] = "CORE"   // precisa ser um ExerciseCategory REAL — valueOf() não é defensivo aqui (ver ExerciseRepositoryImpl.toExercise)
                it[videoRef] = "teste"
                it[thumbRef] = "teste"
                it[isBase] = true
            }
        }
        sinteticos += id
        return id
    }

    @AfterEach
    fun limparSinteticos() {
        // Guarda o `if`: nem todo teste cria sintético (ex. os dois que leem o catálogo INTEIRO),
        // e um `inList` vazio é chão pouco testado em Exposed -- não vale o risco por uma query
        // que não faria nada mesmo.
        if (sinteticos.isNotEmpty()) {
            transaction { ExercisesTable.deleteWhere { ExercisesTable.id inList sinteticos } }
            sinteticos.clear()
        }
    }

    private fun traduzir(id: Uuid, idioma: Idioma, nome: String) = transaction {
        ExerciseTranslationsTable.insert {
            it[exerciseId] = id
            it[locale] = idioma.tag
            it[name] = nome
        }
    }

    private fun <T> valor(r: AppResult<T>): T = (r as AppResult.Success).value

    // -----------------------------------------------------------------------

    /**
     * ⭐ **Exercício sem tradução continua na lista, no piso.**
     *
     * É o teste mais importante do arquivo, e o que justifica o `LEFT`. Com `INNER JOIN`, trocar
     * para inglês faria o catálogo encolher de 923 para o número de traduzidos — **sem erro, sem
     * log, sem nada**. A pessoa concluiria que o app perdeu exercícios.
     *
     * > **Join que decide quem aparece na lista não é detalhe de consulta: é regra de produto
     * > escrita em SQL.**
     */
    @Test
    fun `sem traducao o exercicio aparece com o nome em portugues`() = runBlocking {
        val id = exercicioSintetico()
        val nomePt = transaction { ExercisesTable.selectAll().where { ExercisesTable.id eq id }.single()[ExercisesTable.name] }

        val emIngles = valor(repo.findAll(Idioma.EN))
        val total = transaction { ExercisesTable.selectAll().count() }

        assertEquals(total, emIngles.size.toLong(), "o catálogo encolheu: o join virou INNER?")
        assertEquals(nomePt, emIngles.single { it.id == id }.name)
    }

    /** E com tradução, o nome traduzido vence — o caminho feliz. */
    @Test
    fun `com traducao o nome vem no idioma pedido`() = runBlocking {
        val id = exercicioSintetico()
        val nomePt = transaction { ExercisesTable.selectAll().where { ExercisesTable.id eq id }.single()[ExercisesTable.name] }
        traduzir(id, Idioma.EN, "Flat Bench Press")

        assertEquals("Flat Bench Press", valor(repo.findAll(Idioma.EN)).single { it.id == id }.name)
        assertEquals("Flat Bench Press", valor(repo.findById(id, Idioma.EN))!!.name)

        // E o pt-BR não foi contaminado: ele não passa pelo join.
        assertEquals(nomePt, valor(repo.findById(id, Idioma.PT_BR))!!.name)
    }

    /**
     * O piso NÃO mora na tabela de traduções.
     *
     * Se um dia alguém "completar" o modelo inserindo as 923 linhas em pt-BR, este teste avisa:
     * a leitura em `PADRAO` não faz join, então essas linhas seriam dado morto que ninguém lê, e
     * duas fontes para o mesmo nome — exatamente o que o #37 existe para impedir.
     */
    @Test
    fun `o portugues nao tem linha na tabela de traducoes`() {
        val emPortugues = transaction {
            ExerciseTranslationsTable.selectAll()
                .where { ExerciseTranslationsTable.locale eq Idioma.PT_BR.tag }
                .count()
        }
        assertEquals(0L, emPortugues, "pt-BR mora em exercises.name (V49); linha aqui é dado morto")
    }

    /**
     * ⭐ **A cobertura por idioma, que é o papel do `when` exaustivo que perdemos.**
     *
     * Antes da H.3 ele passava com o inglês vazio; agora (P0.1) roda contra a carga REAL da V53,
     * não mais uma tabela zerada por `@BeforeEach` -- é o teste voltando a medir o que a V49 disse
     * que mediria. O que ele mede é a DISTÂNCIA até o 100%, e falha quando um idioma está
     * parcialmente traduzido — que é o estado perigoso, porque a tela mistura os dois sem avisar.
     *
     * Zero traduzido é honesto (tudo no piso). Metade traduzido é o que ninguém percebe.
     *
     * > **O estado que precisa de alarme não é o vazio: é o pela metade.**
     */
    @Test
    fun `nenhum idioma fica traduzido pela metade`() {
        val total = transaction { ExercisesTable.selectAll().count() }

        val parciais = Idioma.TODOS.filter { it != Idioma.PADRAO }.mapNotNull { idioma ->
            val traduzidos = transaction {
                ExerciseTranslationsTable.selectAll()
                    .where { ExerciseTranslationsTable.locale eq idioma.tag }
                    .count()
            }
            if (traduzidos == 0L || traduzidos == total) null else "${idioma.tag}: $traduzidos de $total"
        }

        assertTrue(
            parciais.isEmpty(),
            "idioma traduzido pela metade mistura os dois na tela, sem avisar: $parciais",
        )
    }

    /**
     * O `CHECK` da V49 espelha o enum `Idioma`, e este teste é o que faz os dois andarem juntos.
     *
     * Sem ele, acrescentar um idioma no contrato e esquecer a migration só apareceria quando a
     * carga de dados estourasse — no dia do deploy, e não no dia do commit.
     */
    @Test
    fun `o banco aceita todo idioma que o contrato declara`() {
        val id = exercicioSintetico()

        Idioma.TODOS.filter { it != Idioma.PADRAO }.forEach { idioma ->
            runCatching { traduzir(id, idioma, "teste-${idioma.tag}") }
                .onFailure {
                    throw AssertionError(
                        "`${idioma.tag}` existe no enum Idioma e o CHECK da V49 recusa. " +
                            "Falta a migration que afrouxa exercise_translations_locale_suportado.",
                        it,
                    )
                }
        }
    }
}
