package dev.rafael.server.checkin

import dev.rafael.contract.checkin.ReportTarget
import dev.rafael.core.result.AppResult
import dev.rafael.server.BancoDeTeste
import dev.rafael.server.Semear
import dev.rafael.server.features.checkin.db.CheckInCommentsTable
import dev.rafael.server.features.checkin.db.CheckInReactionsTable
import dev.rafael.server.features.checkin.db.GroupReportsTable
import dev.rafael.server.features.checkin.db.ModeracaoRepositoryImpl
import dev.rafael.server.features.checkin.db.ModerationActionsTable
import dev.rafael.server.features.checkin.db.SocialRepositoryImpl
import dev.rafael.server.features.checkin.models.AcaoDeModeracao
import dev.rafael.server.features.checkin.models.NovaDenuncia
import dev.rafael.server.features.checkin.models.NovoComentario
import dev.rafael.server.features.checkin.models.TipoDeAcao
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDateTime
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.exceptions.ExposedSQLException
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertThrows
import kotlin.uuid.Uuid

/**
 * Comentários, reações, denúncias e auditoria contra Postgres REAL (V44 e V45).
 *
 * ## As garantias que os fakes das fatias E não reproduzem
 *
 * O `FakeSocialRepository` e o `FakeModeracaoRepository` reproduzem os índices únicos — foi
 * decisão deliberada, e está escrito no KDoc deles. **O que nenhum dos dois alcança:**
 *
 * - os `CHECK` (body não-vazio, motivo não-vazio, **arco exclusivo** do alvo);
 * - os índices únicos **parciais**, que só valem quando a coluna do arco está preenchida;
 * - as **cascatas** — e a mais importante é a que NÃO existe: `moderation_actions` não tem FK no
 *   alvo, para a auditoria sobreviver ao que foi apagado.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SocialModeracaoIntegrationTest {

    private val social = SocialRepositoryImpl()
    private val moderacao = ModeracaoRepositoryImpl()
    private val agora = LocalDateTime.parse("2026-09-09T12:00:00")

    @BeforeAll
    fun setup() {
        BancoDeTeste.dataSource
        BancoDeTeste.limpar()
    }

    private fun <T> ok(r: AppResult<T>): T = (r as AppResult.Success).value

    /** Cenário mínimo: um grupo, um dono e um check-in dele. */
    private class Cena(val dono: Uuid, val grupo: Uuid, val checkIn: Uuid)

    private fun cena(): Cena {
        val dono = Semear.usuario()
        val grupo = Semear.grupo(dono)
        return Cena(dono, grupo, Semear.checkIn(grupo, dono))
    }

    private fun comentario(c: Cena, autor: Uuid = c.dono, texto: String = "boa!"): Uuid {
        val id = Uuid.random()
        runBlocking {
            ok(
                social.comentar(
                    NovoComentario(id, c.checkIn, c.grupo, autor, texto, agora),
                ),
            )
        }
        return id
    }

    private fun denuncia(
        c: Cena,
        alvo: ReportTarget,
        alvoId: Uuid,
        denunciante: Uuid,
        quando: LocalDateTime = agora,
    ) = runBlocking {
        ok(
            moderacao.denunciar(
                NovaDenuncia(Uuid.random(), c.grupo, alvo, alvoId, denunciante, "motivo", quando),
            ),
        )
    }

    // ---- V44: reações ----

    /**
     * ⭐ [INV] "uma reação por pessoa por check-in" (8.2) — **é a PK, não código**.
     *
     * O `reagir` do repositório usa `upsert`. Este teste força um `INSERT` direto para provar que,
     * se alguém trocar o `upsert` por `insert` amanhã, o banco recusa em vez de duplicar.
     */
    @Test
    fun `a PK composta recusa a segunda reacao da mesma pessoa`(): Unit = runBlocking {
        val c = cena()
        ok(social.reagir(c.checkIn, c.grupo, c.dono, "👍", agora))

        assertThrows<ExposedSQLException> {
            transaction {
                CheckInReactionsTable.insert {
                    it[checkInId] = c.checkIn
                    it[groupId] = c.grupo
                    it[userId] = c.dono
                    it[emoji] = "🔥"
                    it[createdAt] = agora
                }
            }
        }
    }

    /** Trocar de reação é `ON CONFLICT DO UPDATE` — uma linha, emoji novo. */
    @Test
    fun `reagir de novo troca o emoji sem duplicar`(): Unit = runBlocking {
        val c = cena()
        ok(social.reagir(c.checkIn, c.grupo, c.dono, "👍", agora))
        ok(social.reagir(c.checkIn, c.grupo, c.dono, "🔥", agora))

        val linhas = transaction {
            CheckInReactionsTable.selectAll()
                .where { CheckInReactionsTable.checkInId eq c.checkIn }
                .map { it[CheckInReactionsTable.emoji] }
        }
        assertEquals(listOf("🔥"), linhas)
    }

    /**
     * O emoji sobrevive ao round-trip.
     *
     * `VARCHAR(16)` e não `CHAR(2)` pelo mesmo motivo da V43: `👍` são 2 unidades UTF-16 e `❤️`
     * leva seletor de variação. Truncar no meio de um par substituto grava lixo **sem erro**.
     */
    @Test
    fun `emoji com seletor de variacao volta inteiro`(): Unit = runBlocking {
        val c = cena()
        ok(social.reagir(c.checkIn, c.grupo, c.dono, "❤️", agora))

        assertEquals("❤️", ok(social.reacoes(listOf(c.checkIn), c.dono))[c.checkIn]!!.single().emoji)
    }

    // ---- V44: comentários ----

    /** O `CHECK` recusa comentário em branco mesmo por `INSERT` direto. */
    @Test
    fun `comentario so com espacos e recusado pelo banco`() {
        val c = cena()

        assertThrows<ExposedSQLException> {
            transaction {
                CheckInCommentsTable.insert {
                    it[id] = Uuid.random()
                    it[checkInId] = c.checkIn
                    it[groupId] = c.grupo
                    it[userId] = c.dono
                    it[body] = "     "
                    it[createdAt] = agora
                }
            }
        }
    }

    /**
     * Apagar o check-in leva comentários e reações (CASCADE da V44).
     *
     * Sem isso, apagar o próprio check-in no mesmo dia (4.11) deixaria comentários órfãos apontando
     * para nada.
     */
    @Test
    fun `apagar o check-in leva comentarios e reacoes`(): Unit = runBlocking {
        val c = cena()
        comentario(c)
        ok(social.reagir(c.checkIn, c.grupo, c.dono, "👍", agora))

        transaction { exec("delete from check_ins where id = '${c.checkIn}'") }

        assertTrue(ok(social.comentarios(c.checkIn)).isEmpty())
        assertTrue(ok(social.reacoes(listOf(c.checkIn), c.dono)).isEmpty())
    }

    // ---- V45: o arco exclusivo ----

    /**
     * ⭐ O `CHECK` do **arco exclusivo**: exatamente um alvo, nunca dois, nunca nenhum.
     *
     * É a garantia que sustenta a decisão de não usar um `target_id` opaco. Sem ela, uma denúncia
     * poderia apontar para um check-in **e** um comentário ao mesmo tempo, e a fila do admin não
     * saberia o que desenhar.
     */
    @Test
    fun `denuncia com dois alvos e recusada`() {
        val c = cena()
        val comentario = comentario(c)

        assertThrows<ExposedSQLException> {
            transaction {
                GroupReportsTable.insert {
                    it[id] = Uuid.random()
                    it[groupId] = c.grupo
                    it[checkInId] = c.checkIn
                    it[commentId] = comentario   // os dois preenchidos
                    it[reporterId] = c.dono
                    it[reason] = "motivo"
                    it[createdAt] = agora
                }
            }
        }
    }

    @Test
    fun `denuncia sem alvo nenhum e recusada`() {
        val c = cena()

        assertThrows<ExposedSQLException> {
            transaction {
                GroupReportsTable.insert {
                    it[id] = Uuid.random()
                    it[groupId] = c.grupo
                    it[checkInId] = null
                    it[commentId] = null
                    it[reporterId] = c.dono
                    it[reason] = "motivo"
                    it[createdAt] = agora
                }
            }
        }
    }

    @Test
    fun `denuncia sem motivo e recusada`() {
        val c = cena()

        assertThrows<ExposedSQLException> {
            transaction {
                GroupReportsTable.insert {
                    it[id] = Uuid.random()
                    it[groupId] = c.grupo
                    it[checkInId] = c.checkIn
                    it[commentId] = null
                    it[reporterId] = c.dono
                    it[reason] = "   "
                    it[createdAt] = agora
                }
            }
        }
    }

    // ---- V45: os índices únicos PARCIAIS ----

    /**
     * ⭐ "Uma denúncia por pessoa por alvo" — e o índice é **parcial**.
     *
     * `WHERE check_in_id IS NOT NULL`: ele só existe para as linhas cujo alvo é check-in. Um índice
     * único comum sobre `(check_in_id, reporter_id)` trataria todos os `NULL` como distintos no
     * Postgres — e as denúncias de comentário passariam batido, o que é certo, mas por acidente.
     */
    @Test
    fun `a mesma pessoa nao denuncia o mesmo check-in duas vezes`(): Unit = runBlocking {
        val c = cena()
        val quemDenuncia = Semear.usuario()

        assertTrue(denuncia(c, ReportTarget.CHECK_IN, c.checkIn, quemDenuncia))
        assertTrue(!denuncia(c, ReportTarget.CHECK_IN, c.checkIn, quemDenuncia), "o índice recusou")
    }

    /** O mesmo, para o outro braço do arco — é o segundo índice parcial. */
    @Test
    fun `a mesma pessoa nao denuncia o mesmo comentario duas vezes`(): Unit = runBlocking {
        val c = cena()
        val alvo = comentario(c)
        val quemDenuncia = Semear.usuario()

        assertTrue(denuncia(c, ReportTarget.COMMENT, alvo, quemDenuncia))
        assertTrue(!denuncia(c, ReportTarget.COMMENT, alvo, quemDenuncia))
    }

    /**
     * ⭐ O índice **não confunde os dois braços**.
     *
     * Denunciar um check-in e um comentário com o mesmo id seria impossível na prática, mas a
     * asserção que importa é outra: **pessoas diferentes denunciando o mesmo alvo passam**, e é
     * disso que o contador da 6.11 vive.
     */
    @Test
    fun `pessoas diferentes denunciam o mesmo alvo`(): Unit = runBlocking {
        val c = cena()
        val um = Semear.usuario()
        val outro = Semear.usuario()

        assertTrue(denuncia(c, ReportTarget.CHECK_IN, c.checkIn, um))
        assertTrue(denuncia(c, ReportTarget.CHECK_IN, c.checkIn, outro))

        assertEquals(2, ok(moderacao.casoAberto(c.grupo, c.checkIn))!!.quantidade)
    }

    /**
     * Depois de resolvido, a mesma pessoa **continua** sem poder denunciar.
     *
     * É por isso que `resolver` marca em vez de apagar: a linha resolvida é o que segura o índice.
     * Apagá-la deixaria a redenúncia infinita.
     */
    @Test
    fun `denuncia resolvida ainda bloqueia a redenuncia`(): Unit = runBlocking {
        val c = cena()
        val quemDenuncia = Semear.usuario()
        denuncia(c, ReportTarget.CHECK_IN, c.checkIn, quemDenuncia)
        ok(moderacao.resolver(c.checkIn, agora))

        assertTrue(!denuncia(c, ReportTarget.CHECK_IN, c.checkIn, quemDenuncia))
    }

    // ---- V45: a auditoria que sobrevive ao alvo ----

    /**
     * ⭐ **A cascata que NÃO existe** — e é a decisão mais importante da V45.
     *
     * Acatar a denúncia de um comentário o **apaga**, e a linha de `group_reports` vai junto por
     * CASCADE. A de `moderation_actions` **fica**, porque ela não tem FK no alvo.
     *
     * Se tivesse CASCADE, a decisão de remover um comentário seria a única que não deixaria rastro
     * — justamente a que mais precisa dele.
     */
    @Test
    fun `a auditoria sobrevive ao comentario apagado`(): Unit = runBlocking {
        val c = cena()
        val alvo = comentario(c)
        val admin = Semear.usuario()
        denuncia(c, ReportTarget.COMMENT, alvo, Semear.usuario())

        ok(
            moderacao.registrar(
                AcaoDeModeracao(
                    Uuid.random(), c.grupo, admin, ReportTarget.COMMENT, alvo,
                    TipoDeAcao.REMOVER_COMENTARIO, agora,
                ),
            ),
        )
        ok(social.apagarComentario(alvo))

        assertEquals(0, denunciasDe(alvo), "a denúncia foi junto (CASCADE)")
        assertEquals(1, acoesSobre(alvo), "a decisão ficou — moderation_actions não tem FK no alvo")
    }

    /**
     * `admin_id` **nulo** é permitido, e é o `ENCERRADO_SEM_JULGAMENTO` da V46.
     *
     * O `ON DELETE SET NULL` também depende disso: o admin apagar a conta não pode apagar o
     * histórico das decisões dele, nem travar a exclusão da conta.
     */
    @Test
    fun `acao sem admin e aceita`(): Unit = runBlocking {
        val c = cena()

        ok(
            moderacao.registrar(
                AcaoDeModeracao(
                    Uuid.random(), c.grupo, adminId = null,
                    alvo = ReportTarget.CHECK_IN, alvoId = c.checkIn,
                    acao = TipoDeAcao.ENCERRADO_SEM_JULGAMENTO, createdAt = agora,
                ),
            ),
        )

        assertNull(
            transaction {
                ModerationActionsTable.selectAll()
                    .where { ModerationActionsTable.targetId eq c.checkIn }
                    .single()[ModerationActionsTable.adminId]
            },
        )
    }

    /**
     * ⭐ Apagar o ADMIN não apaga a decisão dele — vira `NULL` (`ON DELETE SET NULL`).
     *
     * A alternativa (CASCADE) apagaria o histórico de quem saiu, e RESTRICT impediria a pessoa de
     * excluir a própria conta. As duas seriam piores.
     */
    @Test
    fun `apagar o admin anula o autor mas mantem a decisao`(): Unit = runBlocking {
        val c = cena()
        val admin = Semear.usuario()
        ok(
            moderacao.registrar(
                AcaoDeModeracao(
                    Uuid.random(), c.grupo, admin, ReportTarget.CHECK_IN, c.checkIn,
                    TipoDeAcao.MANTER_CHECK_IN, agora,
                ),
            ),
        )

        transaction { exec("delete from users where id = '$admin'") }

        val linha = transaction {
            ModerationActionsTable.selectAll()
                .where { ModerationActionsTable.targetId eq c.checkIn }
                .single()
        }
        assertNull(linha[ModerationActionsTable.adminId], "o autor sumiu")
        assertNotNull(linha[ModerationActionsTable.action], "a decisão ficou")
    }

    /** Vocabulário fechado no banco: uma ação que o código não conhece não entra. */
    @Test
    fun `acao fora do vocabulario e recusada`() {
        val c = cena()

        assertThrows<ExposedSQLException> {
            transaction {
                ModerationActionsTable.insert {
                    it[id] = Uuid.random()
                    it[groupId] = c.grupo
                    it[adminId] = null
                    it[targetType] = "CHECK_IN"
                    it[targetId] = c.checkIn
                    it[action] = "BANIR"
                    it[createdAt] = agora
                }
            }
        }
    }

    /** Apagar o GRUPO leva denúncias E auditoria — a cascata completa da 2.9. */
    @Test
    fun `apagar o grupo leva denuncias e auditoria`(): Unit = runBlocking {
        val c = cena()
        denuncia(c, ReportTarget.CHECK_IN, c.checkIn, Semear.usuario())
        ok(
            moderacao.registrar(
                AcaoDeModeracao(
                    Uuid.random(), c.grupo, null, ReportTarget.CHECK_IN, c.checkIn,
                    TipoDeAcao.MANTER_CHECK_IN, agora,
                ),
            ),
        )

        transaction { exec("delete from groups where id = '${c.grupo}'") }

        assertEquals(0, denunciasDe(c.checkIn))
        assertEquals(0, acoesSobre(c.checkIn))
    }

    // ---- utilidades ----

    private fun denunciasDe(alvo: Uuid): Int = transaction {
        GroupReportsTable.selectAll()
            .where {
                (GroupReportsTable.checkInId eq alvo) or (GroupReportsTable.commentId eq alvo)
            }
            .count().toInt()
    }

    private fun acoesSobre(alvo: Uuid): Int = transaction {
        ModerationActionsTable.selectAll()
            .where { ModerationActionsTable.targetId eq alvo }
            .count().toInt()
    }
}
