package dev.rafael.server.exercise

import dev.rafael.contract.i18n.Idioma
import dev.rafael.core.result.AppResult
import dev.rafael.server.BancoDeTeste
import dev.rafael.server.features.exercise.db.ExerciseRepositoryImpl
import dev.rafael.server.features.exercise.db.ExerciseTranslationsTable
import dev.rafael.server.features.exercise.db.ExercisesTable
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
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
     * Limpeza POR TESTE, e não só por classe.
     *
     * O `limpar()` da classe roda uma vez; aqui cada teste insere tradução para o MESMO exercício
     * (o primeiro da tabela), então sem isto o segundo teste esbarraria na PK composta do primeiro.
     *
     * A alternativa seria cada teste usar um exercício diferente, que é a lição de isolamento por
     * CHAVE do `AchievementGrantIntegrationTest`. Não serve aqui: metade destes testes conta linhas
     * do catálogo INTEIRO, então o que precisa ficar previsível é o conjunto, não uma linha.
     */
    @BeforeEach
    fun semTraducoes() {
        transaction { ExerciseTranslationsTable.deleteAll() }
    }

    private fun algumExercicio(): Pair<Uuid, String> = transaction {
        ExercisesTable.selectAll().limit(1).single()
            .let { it[ExercisesTable.id] to it[ExercisesTable.name] }
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
     * para inglês faria o catálogo encolher de 963 para o número de traduzidos — **sem erro, sem
     * log, sem nada**. A pessoa concluiria que o app perdeu exercícios.
     *
     * > **Join que decide quem aparece na lista não é detalhe de consulta: é regra de produto
     * > escrita em SQL.**
     */
    @Test
    fun `sem traducao o exercicio aparece com o nome em portugues`() = runBlocking {
        val (id, nomePt) = algumExercicio()

        val emIngles = valor(repo.findAll(Idioma.EN))
        val total = transaction { ExercisesTable.selectAll().count() }

        assertEquals(total, emIngles.size.toLong(), "o catálogo encolheu: o join virou INNER?")
        assertEquals(nomePt, emIngles.single { it.id == id }.name)
    }

    /** E com tradução, o nome traduzido vence — o caminho feliz. */
    @Test
    fun `com traducao o nome vem no idioma pedido`() = runBlocking {
        val (id, nomePt) = algumExercicio()
        traduzir(id, Idioma.EN, "Flat Bench Press")

        assertEquals("Flat Bench Press", valor(repo.findAll(Idioma.EN)).single { it.id == id }.name)
        assertEquals("Flat Bench Press", valor(repo.findById(id, Idioma.EN))!!.name)

        // E o pt-BR não foi contaminado: ele não passa pelo join.
        assertEquals(nomePt, valor(repo.findById(id, Idioma.PT_BR))!!.name)
    }

    /**
     * O piso NÃO mora na tabela de traduções.
     *
     * Se um dia alguém "completar" o modelo inserindo as 963 linhas em pt-BR, este teste avisa:
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
     * Hoje ele passa com o inglês vazio, e isso é correto: a V49 cria o schema e a carga dos ~960
     * nomes é a H.3. O que ele mede é a DISTÂNCIA até o 100%, e falha quando um idioma está
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
        val (id, _) = algumExercicio()

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
