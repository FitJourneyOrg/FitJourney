package dev.rafael.server.exercise

import dev.rafael.contract.i18n.Idioma
import dev.rafael.core.result.AppResult
import dev.rafael.server.BancoDeTeste
import dev.rafael.server.features.exercise.db.ExerciseRepositoryImpl
import dev.rafael.server.features.exercise.db.ExercisesTable
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.exceptions.ExposedSQLException
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertThrows

/**
 * Trava a **V74** (incremento, barra livre e carga invertida), contra Postgres REAL.
 *
 * ## O defeito
 *
 * `Progressao.decidir()`/`calibrar()` exigem, por exercício, o incremento de carga do equipamento,
 * se é barra livre e — achado em 05/11 — se a carga é INVERTIDA. Nenhum dos três existia em lugar
 * nenhum do código: os nomes só apareciam como PARÂMETRO de `Progressao`, sem produtor. Sem eles,
 * o fio do RIR não tem o que passar para o motor.
 *
 * ## O que este arquivo afirma, e por que não é redundante com a migration
 *
 * A V74 já tem autoconferência própria (`RAISE EXCEPTION` se as contagens divergirem), então
 * repetir os números aqui não acrescentaria nada. O que só este teste pode afirmar é que os
 * **CHECKs recusam de verdade** o que prometem, e que as colunas chegam ao modelo pelo
 * `toExercise()` REAL — coluna nova em migration que ninguém mapeia em `ExercisesTable`/
 * `toExercise` não quebra nada, só devolve o default para sempre.
 *
 * A derivação em si mora em SQL (a migration), não em Kotlin: não existe função pura a testar, e
 * duplicá-la em Kotlin criaria uma segunda fonte da verdade para a mesma regra. Quem decide é a
 * coluna, porque é dela que o motor vai ler.
 *
 * ## Por que quase tudo aqui lê a COLUNA e só um teste passa pelo repositório
 *
 * Primeira rodada: as 8 falharam na mesma linha, um helper que fazia `findAll() as Success` e
 * recebeu `Failure` — o cast cego engoliu a causa e transformou um problema de porta em oito
 * falhas idênticas. Agora a dependência do repositório está isolada em
 * [os tres campos chegam ao modelo pelo mapeamento real], com a mensagem do `AppError` à vista,
 * e o resto lê a coluna direto. Teste de migration que depende da camada inteira para afirmar que
 * uma coluna existe mistura dois defeitos possíveis numa falha só.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SemanticaDeCargaIntegrationTest {

    @BeforeAll
    fun setup() {
        BancoDeTeste.dataSource
        BancoDeTeste.limpar()
    }

    /** Os três campos da V74, lidos direto da coluna. */
    private data class Trinca(
        val incremento: Double?,
        val barraLivre: Boolean,
        val cargaInvertida: Boolean,
    )

    private fun trincaDe(nome: String): Trinca = transaction {
        ExercisesTable.selectAll()
            .first { it[ExercisesTable.name] == nome }
            .let {
                Trinca(
                    it[ExercisesTable.incremento],
                    it[ExercisesTable.barraLivre],
                    it[ExercisesTable.cargaInvertida],
                )
            }
    }

    /**
     * O caminho inteiro: coluna -> `ExercisesTable` -> `toExercise()` -> `Exercise`. É aqui que uma
     * coluna esquecida no mapeamento apareceria, e a migration não tem como ver isso.
     *
     * Caminho de falha embutido: `findAll` devolvendo `Failure` falha COM a mensagem do `AppError`,
     * em vez de estourar um `ClassCastException` que não diz nada.
     */
    @Test
    fun `os tres campos chegam ao modelo pelo mapeamento real`() = runBlocking {
        val catalogo = when (val r = ExerciseRepositoryImpl().findAll(Idioma.PT_BR)) {
            is AppResult.Success -> r.value
            is AppResult.Failure -> error("findAll(PT_BR) falhou: ${r.error}")
        }

        val agachamento = catalogo.first { it.name == "Agachamento Livre com Barra" }
        assertEquals(2.5, agachamento.incremento)
        assertTrue(agachamento.barraLivre) { "BARBELL tem de ser barra livre" }
        assertFalse(agachamento.cargaInvertida)

        val assistida = catalogo.first { it.name == "Barra Fixa Assistida na Máquina" }
        assertEquals(2.5, assistida.incremento)
        assertFalse(assistida.barraLivre) { "MACHINE não é barra livre" }
        assertTrue(assistida.cargaInvertida) { "a assistida é o caso de carga invertida" }

        val flexao = catalogo.first { it.name == "Flexão" }
        assertNull(flexao.incremento) { "peso do corpo não tem incremento de carga" }
        assertFalse(flexao.barraLivre)
        assertFalse(flexao.cargaInvertida)
    }

    /**
     * O halter é POR HALTER, 1 kg, e não 2: o rack brasileiro anda de 1 em 1 até 10 kg, que é onde
     * o iniciante vive, e 2 kg faz 6->8 (+33%) — medido, nunca converge
     * (`tools/motor_sim/sim_incremento.py`).
     */
    @Test
    fun `halter tem incremento de um quilo e o smith nao e barra livre`() {
        assertEquals(1.0, trincaDe("Agachamento Goblet com Halter").incremento)

        val smith = trincaDe("Supino Reto no Smith")
        assertEquals(2.5, smith.incremento)
        assertFalse(smith.barraLivre) { "SMITH tem barra, mas o trilho é guiado" }
    }

    /**
     * Marcados por nome explícito, não por `LIKE '%Assistid%'`: os outros cinco "assistidos" do
     * catálogo são assistência de banco, parceiro ou faixa, sem carga selecionável. Se alguém
     * trocar os nomes por um LIKE, esta contagem passa de 4.
     */
    @Test
    fun `carga invertida existe exatamente nas quatro maquinas assistidas`() {
        val invertidos = transaction {
            ExercisesTable.selectAll()
                .filter { it[ExercisesTable.cargaInvertida] }
                .map { it[ExercisesTable.name] }
                .toSet()
        }

        assertEquals(
            setOf(
                "Barra Fixa Assistida na Máquina",
                "Mergulho Assistido na Máquina",
                "Mergulho Assistido para Tríceps",
                "Subida Unilateral Assistida na Máquina",
            ),
            invertidos,
        )
    }

    /**
     * Caminho de falha (C2): o `[INV]` de `barra_livre` é do BANCO, não do código. Enquanto a
     * coluna for função pura de `equipment`, guardá-la é duplicar estado derivável — o CHECK é o
     * que impede as duas versões de discordarem (mesmo arranjo da V65 para `is_base` x
     * `relevancia`).
     */
    @Test
    fun `banco recusa barra livre que discorda do equipamento`() {
        assertThrows<ExposedSQLException> {
            transaction {
                ExercisesTable.update({ ExercisesTable.name eq "Supino Reto no Smith" }) {
                    it[barraLivre] = true
                }
            }
        }
        assertFalse(trincaDe("Supino Reto no Smith").barraLivre)
    }

    /**
     * Caminho de falha (C2): o inverso também é barrado — tirar a barra livre de quem é BARBELL.
     * A condição do CHECK é de igualdade, não de implicação, justamente para pegar os dois lados.
     */
    @Test
    fun `banco recusa barbell sem barra livre`() {
        assertThrows<ExposedSQLException> {
            transaction {
                ExercisesTable.update({ ExercisesTable.name eq "Agachamento Livre com Barra" }) {
                    it[barraLivre] = false
                }
            }
        }
        assertTrue(trincaDe("Agachamento Livre com Barra").barraLivre)
    }

    /**
     * Caminho de falha (C2): incremento zero travaria a progressão —
     * `Progressao.paraIncremento()` tem `require(incremento > 0)` e `cargaAposSalto()` promete
     * "nunca menos que UM incremento acima". Mesmo CHECK que a V69 usa para `carga_kg`.
     */
    @Test
    fun `banco recusa incremento zero`() {
        assertThrows<ExposedSQLException> {
            transaction {
                ExercisesTable.update({ ExercisesTable.name eq "Agachamento Livre com Barra" }) {
                    it[incremento] = 0.0
                }
            }
        }
        assertEquals(2.5, trincaDe("Agachamento Livre com Barra").incremento)
    }

    /**
     * Caminho de falha (C2): carga invertida sem unidade de carga não tem sinal para trocar.
     * Pega o caso de alguém marcar uma assistida de faixa elástica, que não tem kg nenhum.
     */
    @Test
    fun `banco recusa carga invertida sem incremento`() {
        assertThrows<ExposedSQLException> {
            transaction {
                ExercisesTable.update({ ExercisesTable.name eq "Flexão" }) {
                    it[cargaInvertida] = true
                }
            }
        }
        assertFalse(trincaDe("Flexão").cargaInvertida)
    }

    /**
     * `incremento` NULL não é "não curado", é "não se aplica": o chamador não invoca o motor,
     * porque `decidir()`/`calibrar()` exigem `Double` não-nulo e não existe fallback por
     * repetição. Este teste existe para que o dia em que alguém "consertar" isso preenchendo os
     * nulos com 0,0 apareça aqui, e não em produção — e para deixar o tamanho do buraco medido:
     * é débito P0 aberto (progressão inexistente para treino em casa).
     */
    @Test
    fun `peso do corpo e faixa elastica nao tem incremento e o preenchido e positivo`() {
        val porEquipamento = transaction {
            ExercisesTable.selectAll().map {
                it[ExercisesTable.equipment] to it[ExercisesTable.incremento]
            }
        }

        assertTrue(porEquipamento.filter { it.first == "BODYWEIGHT" }.all { it.second == null }) {
            "peso do corpo tem de ficar sem incremento"
        }
        assertTrue(porEquipamento.filter { it.first == "BAND" }.all { it.second == null }) {
            "faixa elástica não tem carga em kg"
        }
        assertTrue(porEquipamento.none { it.second != null && it.second!! <= 0.0 }) {
            "incremento preenchido tem de ser positivo"
        }
    }
}
