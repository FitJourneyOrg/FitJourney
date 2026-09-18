package dev.rafael.server.friendship

import dev.rafael.server.BancoDeTeste
import dev.rafael.server.CodigoDeTeste
import dev.rafael.server.Semear
import dev.rafael.core.result.AppResult
import dev.rafael.server.features.friendship.db.BlocksTable
import dev.rafael.server.features.friendship.db.FriendshipRepositoryImpl
import dev.rafael.server.features.friendship.db.FriendshipsTable
import dev.rafael.server.features.friendship.services.FriendshipPolicy
import dev.rafael.server.features.user.db.UsersTable
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDateTime
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.exceptions.ExposedSQLException
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertThrows
import kotlin.uuid.Uuid

/**
 * Amizades e bloqueios contra Postgres REAL (V40).
 *
 * ## O que só o banco prova aqui
 *
 * O `FriendshipPolicy` cobre as regras em Kotlin puro e o `FriendshipServiceTest` cobre a
 * orquestração com dublês. **O que nenhum dos dois alcança é o par canônico** — a decisão central
 * do #35: a PK é `(user_a, user_b)` com `user_a` = menor uuid, e é ela que torna o pedido cruzado
 * duplicado **impossível no banco**, não uma checagem que alguém pode esquecer.
 *
 * Um dublê pode sempre aceitar a segunda linha. O Postgres não.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AmizadeIntegrationTest {

    private val repo = FriendshipRepositoryImpl()
    private val agora = LocalDateTime.parse("2026-09-09T12:00:00")

    @BeforeAll
    fun setup() {
        BancoDeTeste.dataSource
        BancoDeTeste.limpar()
    }

    private fun <T> ok(r: AppResult<T>): T = (r as AppResult.Success).value

    // ---- o par canônico ----

    /**
     * ⭐ **A ordem do par não importa para o banco** — é o coração da V40.
     *
     * `pedir(a, b)` e `pedir(b, a)` escrevem na MESMA linha, porque a chave é ordenada por uuid. Sem
     * isso, duas pessoas se adicionando ao mesmo tempo criariam duas amizades, e "somos amigos?"
     * teria duas respostas.
     */
    @Test
    fun `a chave e o par canonico, nao a ordem do pedido`(): Unit = runBlocking {
        val a = Semear.usuario()
        val b = Semear.usuario()

        assertTrue(ok(repo.pedir(a, b, agora)))

        // O segundo pedido, na ordem inversa, encontra a linha que já existe.
        assertFalse(ok(repo.pedir(b, a, agora)), "não criou uma segunda linha")
        assertEquals(1, linhasEntre(a, b))
    }

    /**
     * O `requested_by` existe porque a chave **perde** essa informação.
     *
     * Ordenar por uuid apaga quem pediu — e quem pediu decide quem pode aceitar. É a coluna que o
     * par canônico obrigou a criar.
     */
    @Test
    fun `quem pediu fica gravado mesmo saindo da chave`(): Unit = runBlocking {
        val a = Semear.usuario()
        val b = Semear.usuario()
        ok(repo.pedir(b, a, agora))

        assertEquals(b, ok(repo.entre(a, b))!!.requestedBy)
    }

    /** A PK recusa a segunda linha, mesmo forçando por `INSERT` direto. */
    @Test
    fun `o banco recusa duas linhas para o mesmo par`(): Unit = runBlocking {
        val a = Semear.usuario()
        val b = Semear.usuario()
        ok(repo.pedir(a, b, agora))

        val (menor, maior) = if (a.toString() < b.toString()) a to b else b to a
        assertThrows<ExposedSQLException> {
            transaction {
                FriendshipsTable.insert {
                    it[userA] = menor
                    it[userB] = maior
                    it[requestedBy] = a
                    it[status] = "PENDENTE"
                    it[createdAt] = agora
                }
            }
        }
    }

    /**
     * `responder` só muda o que está **pendente**.
     *
     * O `WHERE status = 'PENDENTE'` no update é o que faz responder duas vezes não desfazer a
     * primeira decisão — e é uma cláusula de SQL que só o banco exercita.
     */
    @Test
    fun `responder duas vezes nao muda a decisao`(): Unit = runBlocking {
        val a = Semear.usuario()
        val b = Semear.usuario()
        ok(repo.pedir(a, b, agora))

        assertTrue(ok(repo.responder(a, b, FriendshipPolicy.Estado.ACEITA, agora)))
        assertFalse(
            ok(repo.responder(a, b, FriendshipPolicy.Estado.RECUSADA, agora)),
            "já não está pendente",
        )
        assertEquals(FriendshipPolicy.Estado.ACEITA, ok(repo.entre(a, b))!!.status)
    }

    // ---- bloqueio ----

    /**
     * ⭐ Bloqueio é **direcional**, ao contrário da amizade.
     *
     * A PK `(blocker_id, blocked_id)` NÃO é ordenada: A bloquear B é um fato diferente de B
     * bloquear A. O contraste com `friendships`, duas tabelas de distância, é o ponto — e trocar
     * uma pela outra por engano seria invisível num fake.
     */
    @Test
    fun `bloqueio guarda a direcao`(): Unit = runBlocking {
        val a = Semear.usuario()
        val b = Semear.usuario()

        ok(repo.bloquear(a, b, agora))

        assertTrue(ok(repo.bloqueouMe(alvo = a, quemPergunta = b)), "A bloqueou B")
        assertFalse(ok(repo.bloqueouMe(alvo = b, quemPergunta = a)), "B não bloqueou A")
    }

    /** `haBloqueioEntre` ignora a direção — para barrar, qualquer um dos lados serve. */
    @Test
    fun `ha bloqueio entre e simetrico, ao contrario do registro`(): Unit = runBlocking {
        val a = Semear.usuario()
        val b = Semear.usuario()
        ok(repo.bloquear(a, b, agora))

        assertTrue(ok(repo.haBloqueioEntre(a, b)))
        assertTrue(ok(repo.haBloqueioEntre(b, a)))
    }

    /**
     * ⭐ **Bloquear apaga a amizade na MESMA transação.**
     *
     * Duas escritas que precisam acontecer juntas: se a amizade sobrevivesse ao bloqueio, a pessoa
     * apareceria na lista de amigos de quem a bloqueou. É atomicidade — nenhum fake reproduz.
     */
    @Test
    fun `bloquear apaga a amizade existente`(): Unit = runBlocking {
        val a = Semear.usuario()
        val b = Semear.usuario()
        ok(repo.pedir(a, b, agora))
        ok(repo.responder(a, b, FriendshipPolicy.Estado.ACEITA, agora))

        ok(repo.bloquear(a, b, agora))

        assertEquals(0, linhasEntre(a, b), "a amizade saiu junto")
        assertEquals(0, ok(repo.contarAmizades(a)))
    }

    // ---- users.code (V40) ----

    /**
     * O `UNIQUE` do código de usuário.
     *
     * É o que o `LimitadorDeResgate` e o `regenerate` assumem — e o que faz o código ser um
     * identificador de verdade, e não uma sugestão.
     */
    @Test
    fun `codigo de usuario e unico`() {
        val primeiro = Semear.usuario()
        val codigo = transaction {
            UsersTable.selectAll().where { UsersTable.id eq primeiro }.single()[UsersTable.code]
        }

        assertThrows<ExposedSQLException> {
            transaction {
                val id = Uuid.random()
                UsersTable.insert {
                    it[UsersTable.id] = id
                    it[firebaseUid] = "uid-$id"
                    it[email] = "$id@teste.local"
                    it[displayName] = "Outro"
                    it[code] = codigo   // repetido de propósito
                }
            }
        }
    }

    /**
     * O `CHECK` do alfabeto recusa `O`, `0`, `I` e `1`.
     *
     * Esses códigos são **ditados por voz** e digitados à mão; o alfabeto reduzido é a defesa
     * contra o erro de quem escuta. A regra vive no banco para valer também em `INSERT` por script.
     */
    @Test
    fun `codigo fora do alfabeto e recusado`() {
        assertThrows<ExposedSQLException> {
            transaction {
                val id = Uuid.random()
                UsersTable.insert {
                    it[UsersTable.id] = id
                    it[firebaseUid] = "uid-$id"
                    it[email] = "$id@teste.local"
                    it[displayName] = "Confuso"
                    it[code] = "O0I1ABCD"
                }
            }
        }
    }

    /**
     * ⭐ O `CodigoDeTeste` produz código VÁLIDO — e este teste é o que impede a regressão de 2026-08-31.
     *
     * A V40 apertou `users.code` e derrubou **51 de 62** testes de integração, porque todos inserem
     * direto na tabela. Se o alfabeto mudar de novo, é aqui que aparece — e não em cinquenta
     * `@BeforeAll` ao mesmo tempo.
     */
    @Test
    fun `o gerador de codigo dos testes respeita o CHECK`() {
        repeat(20) { Semear.usuario() }
        assertTrue(true, "nenhum INSERT estourou")
    }

    // ---- cascatas ----

    /**
     * As duas cascatas da V40 — e um teste que já falhou por contar demais.
     *
     * A primeira versão afirmava `BlocksTable.count() == 0`, o que **assume um banco vazio**. Com o
     * container compartilhado a limpeza é por CLASSE, não por teste: os bloqueios dos testes
     * anteriores ainda estavam lá, e o teste falhou por um motivo que não tinha nada a ver com a
     * cascata.
     *
     * > **Asserção sobre a tabela inteira assume um isolamento que a suíte não dá.** Afirme sobre o
     * > cenário do teste — o par, o grupo, o usuário —, nunca sobre o `count()` global.
     */
    @Test
    fun `apagar o usuario leva amizades e bloqueios`(): Unit = runBlocking {
        val a = Semear.usuario()
        val b = Semear.usuario()
        ok(repo.pedir(a, b, agora))
        ok(repo.bloquear(b, a, agora))

        transaction { exec("delete from users where id = '$a'") }

        assertEquals(0, linhasEntre(a, b))
        assertEquals(0, bloqueiosDe(b), "o bloqueio de B contra A saiu junto com A")
    }

    // ---- utilidades ----

    /** Bloqueios FEITOS por alguém. Escopado ao cenário — ver o KDoc do teste da cascata. */
    private fun bloqueiosDe(quem: Uuid): Int = transaction {
        BlocksTable.selectAll().where { BlocksTable.blockerId eq quem }.count().toInt()
    }

    private fun linhasEntre(um: Uuid, outro: Uuid): Int {
        val (menor, maior) = if (um.toString() < outro.toString()) um to outro else outro to um
        return transaction {
            FriendshipsTable.selectAll()
                .where { (FriendshipsTable.userA eq menor) and (FriendshipsTable.userB eq maior) }
                .count().toInt()
        }
    }
}
