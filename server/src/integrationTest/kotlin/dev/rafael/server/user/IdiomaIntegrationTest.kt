package dev.rafael.server.user

import dev.rafael.contract.i18n.Idioma
import dev.rafael.core.result.AppResult
import dev.rafael.server.BancoDeTeste
import dev.rafael.server.CodigoDeTeste
import dev.rafael.server.Semear
import dev.rafael.server.features.user.db.UserRepositoryImpl
import dev.rafael.server.features.user.db.UsersTable
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.exceptions.ExposedSQLException
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertThrows
import kotlin.uuid.Uuid

/**
 * `users.locale` contra Postgres REAL (V47, ARCH #37).
 *
 * ## O que só o banco prova aqui
 *
 * O `IdiomaPolicyTest` cobre a cadeia de fallback em Kotlin puro, e o `UserServiceTest` cobre a
 * recusa do PATCH com dublê. **O que nenhum dos dois alcança é a divergência entre duas fontes da
 * mesma verdade**: o `CHECK` da V47 lista os idiomas permitidos, e o enum `Idioma` lista os mesmos.
 *
 * São dois lugares dizendo a mesma coisa, e é exatamente o padrão que já custou caro neste projeto
 * (o `canDelete` da fatia E.2). Aqui a divergência tem um sintoma feio: adicionar um idioma no enum
 * sem a migration faz o `PATCH /me` passar por toda a validação do Kotlin e **estourar no UPDATE**,
 * em produção, com erro de banco na cara do usuário.
 *
 * > **Vocabulário fechado nos dois lados precisa de um teste que compare os dois lados.**
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class IdiomaIntegrationTest {

    private val repo = UserRepositoryImpl()

    @BeforeAll
    fun setup() {
        BancoDeTeste.dataSource
        BancoDeTeste.limpar()
    }

    private fun <T> ok(r: AppResult<T>): T = (r as AppResult.Success).value

    // ---- as duas fontes da mesma verdade ----

    /**
     * ⭐ **O CHECK do banco aceita EXATAMENTE os idiomas do enum.**
     *
     * O teste é escrito pelo comportamento e não lendo o texto da constraint: para cada valor do
     * enum, o banco tem de aceitar; para um valor fora dele, tem de recusar.
     *
     * Ler o SQL da constraint provaria menos e quebraria por formatação. Isto quebra pelo motivo
     * certo: alguém acrescentou um idioma de um lado só.
     */
    @Test
    fun `o CHECK da V47 aceita todos os idiomas do enum`() {
        Idioma.TODOS.forEach { idioma ->
            val id = Uuid.random()
            transaction {
                UsersTable.insert {
                    it[UsersTable.id] = id
                    it[firebaseUid] = "uid-$id"
                    it[email] = "$id@teste.local"
                    it[displayName] = "Atleta"
                    it[code] = CodigoDeTeste.de(id)
                    it[locale] = idioma.tag
                }
            }
            val gravado = transaction {
                UsersTable.selectAll().where { UsersTable.id eq id }.single()[UsersTable.locale]
            }
            assertEquals(idioma.tag, gravado, "o banco recusou ou alterou ${idioma.tag}")
        }
    }

    /**
     * ⭐ E recusa o que o enum não tem.
     *
     * É a outra metade da asserção acima, e sem ela o teste passaria com um `CHECK` que aceita
     * qualquer coisa. **Uma verificação de vocabulário fechado que só testa o que é aceito não
     * testa o fechamento.**
     */
    @Test
    fun `o CHECK recusa idioma que o enum nao tem`() {
        assertThrows<ExposedSQLException> {
            transaction {
                val id = Uuid.random()
                UsersTable.insert {
                    it[UsersTable.id] = id
                    it[firebaseUid] = "uid-$id"
                    it[email] = "$id@teste.local"
                    it[displayName] = "Poliglota"
                    it[code] = CodigoDeTeste.de(id)
                    it[locale] = "es"
                }
            }
        }
    }

    /** E recusa lixo bem formado. O `CHECK` é de vocabulário, não de formato. */
    @Test
    fun `o CHECK recusa tag bem formada mas inexistente`() {
        assertThrows<ExposedSQLException> {
            transaction {
                val id = Uuid.random()
                UsersTable.insert {
                    it[UsersTable.id] = id
                    it[firebaseUid] = "uid-$id"
                    it[email] = "$id@teste.local"
                    it[displayName] = "Ninguem"
                    it[code] = CodigoDeTeste.de(id)
                    it[locale] = "xx-XX"
                }
            }
        }
    }

    // ---- o default, e quem já existia ----

    /**
     * ⭐ Quem já existia continua em pt-BR.
     *
     * A V47 não tem backfill: o `DEFAULT` cobre as linhas existentes. Seguir o idioma do APARELHO
     * seria o comportamento de um app internacional, e hoje seria um brasileiro abrindo o app amanhã
     * em inglês sem ter pedido nada.
     *
     * > **Recurso que muda a experiência de quem não pediu nada precisa de um gesto da pessoa.**
     *
     * O `Semear.usuario()` insere SEM tocar em `locale`, exatamente como uma linha anterior à V47.
     */
    @Test
    fun `linha que nao escolheu idioma nasce em portugues`(): Unit = runBlocking {
        val id = Semear.usuario()

        assertEquals(Idioma.PT_BR, ok(repo.findById(id))!!.idioma)
    }

    // ---- round-trip ----

    @Test
    fun `trocar o idioma sobrevive ao round-trip`(): Unit = runBlocking {
        val id = Semear.usuario()

        ok(repo.updateIdioma(id, Idioma.EN))

        assertEquals(Idioma.EN, ok(repo.findById(id))!!.idioma)
    }

    /**
     * O repositório recebe o ENUM, não a tag.
     *
     * É o que impede alguém de mandar `"es"` cru e receber um erro de banco onde deveria haver uma
     * recusa de validação. Este teste não tem como falhar em tempo de execução, e é essa a questão:
     * ele documenta que a assinatura é a defesa, e falharia na COMPILAÇÃO se alguém a afrouxasse.
     */
    @Test
    fun `criar usuario grava o idioma padrao explicitamente`(): Unit = runBlocking {
        val id = Uuid.random()
        val u = ok(
            repo.create(
                id = id,
                firebaseUid = "uid-$id",
                email = "$id@teste.local",
                displayName = "Novo",
                code = CodigoDeTeste.de(id),
            ),
        )

        assertEquals(Idioma.PADRAO, u.idioma, "o objeto devolvido")

        val naColuna = transaction {
            UsersTable.selectAll().where { UsersTable.id eq id }.single()[UsersTable.locale]
        }
        assertEquals(Idioma.PADRAO.tag, naColuna, "e a coluna concordam")
    }

    // ---- o tamanho da coluna ----

    /**
     * `VARCHAR(5)` cabe toda tag do enum, hoje e no dia em que a lista crescer.
     *
     * Cinco é o suficiente para `pt-BR` e para qualquer `xx-YY`. Uma tag com script, como
     * `zh-Hant-TW`, não caberia, e este teste é onde isso apareceria: falha no build em vez de
     * truncamento silencioso no `INSERT`.
     */
    @Test
    fun `toda tag do enum cabe na coluna`() {
        Idioma.TODOS.forEach {
            assertTrue(it.tag.length <= 5, "a tag ${it.tag} não cabe em VARCHAR(5); a V47 precisa mudar")
        }
    }
}
